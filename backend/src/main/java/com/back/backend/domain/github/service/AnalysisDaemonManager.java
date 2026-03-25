package com.back.backend.domain.github.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 외부 분석 스크립트를 상주 프로세스(데몬)로 관리한다.
 *
 * 기존 방식(요청마다 프로세스 생성)의 문제:
 *   - Python/Node 인터프리터 기동 비용이 분석마다 발생 (~200~500ms)
 *   - AST 라이브러리 import 시간 포함 시 더 길어짐
 *
 * 데몬 방식:
 *   - @PostConstruct 시점에 각 스크립트를 --daemon 플래그로 한 번 기동
 *   - 이후 stdin/stdout JSON 라인 프로토콜로 통신 (프로세스 재사용)
 *   - 데몬 미기동(스크립트 미설치 등) 시 null 반환 → StaticAnalysisService가 프로세스 방식으로 폴백
 *
 * 프로토콜 (newline-delimited JSON):
 *   요청: {"repoRoot":"/path","files":["a.kt","b.kt"]}  ← files=null 이면 전체 분석
 *   응답: [{fqn:..., file_path:..., ...}, ...]
 *   스크립트 측 시작 신호: "READY" 한 줄 출력 후 요청 대기
 *
 * TODO: 스크립트(kotlin_analyzer.py 등)에 --daemon 모드 구현 필요.
 *   현재는 데몬 기동 실패 시 기존 프로세스 방식으로 자동 폴백되므로 점진적 전환 가능.
 */
@Service
public class AnalysisDaemonManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AnalysisDaemonManager.class);

    // 스크립트가 준비됐을 때 stdout으로 출력하는 시그널
    private static final String READY_SIGNAL = "READY";
    // READY 대기 최대 시간 (ms)
    private static final int STARTUP_TIMEOUT_MS = 15_000;
    // READY 폴링 간격 (ms)
    private static final int POLL_INTERVAL_MS = 100;

    // language key → [interpreter, scriptName]
    // TODO: 스크립트별 데몬 지원 여부에 따라 목록 조정
    private static final Map<String, String[]> DAEMON_CONFIG = Map.of(
            "kotlin", new String[]{"python3", "kotlin_analyzer.py"},
            "python", new String[]{"python3", "python_analyzer.py"},
            "ts",     new String[]{"node",    "ts_analyzer.js"},
            "go",     new String[]{"python3", "go_analyzer.py"},
            "rust",   new String[]{"python3", "rust_analyzer.py"},
            "c",      new String[]{"python3", "c_analyzer.py"}
    );

    private final String scriptsBasePath;
    // 기동 성공한 데몬만 등록됨
    private final Map<String, DaemonHandle> daemons = new ConcurrentHashMap<>();

    private record DaemonHandle(
            Process process,
            BufferedWriter writer,
            BufferedReader reader,
            ReentrantLock lock
    ) {}

    public AnalysisDaemonManager(
            @Value("${analysis.scripts-base-path:/opt/analysis-scripts}") String scriptsBasePath
    ) {
        this.scriptsBasePath = scriptsBasePath;
    }

    @PostConstruct
    public void startAll() {
        DAEMON_CONFIG.forEach((lang, config) -> {
            try {
                startDaemon(lang, config[0], config[1]);
            } catch (Exception e) {
                log.warn("[{}] daemon start failed — will use process-per-request fallback: {}",
                        lang, e.getMessage());
            }
        });
    }

    /**
     * 지정한 언어의 데몬에 분석 요청을 전송한다.
     *
     * @param language  언어 키 ("kotlin", "python", "ts", "go", "rust", "c")
     * @param repoRoot  분석할 repo 루트 경로
     * @param files     분석 대상 파일 상대 경로 목록. empty → 전체 분석
     * @return 스크립트 응답 JSON 문자열, 또는 null (데몬 없거나 실패 시 → 호출자가 폴백)
     */
    public String callRaw(String language, Path repoRoot, Optional<Set<String>> files) {
        DaemonHandle handle = daemons.get(language);
        if (handle == null || !handle.process().isAlive()) {
            if (handle != null) {
                log.warn("[{}] daemon died — removing, next call will use process fallback", language);
                daemons.remove(language);
            }
            return null;
        }

        handle.lock().lock();
        try {
            String filesJson = files
                    .map(set -> set.stream()
                            .map(f -> "\"" + f.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                            .collect(Collectors.joining(",", "[", "]")))
                    .orElse("null");
            String request = "{\"repoRoot\":\"" + repoRoot.toString().replace("\\", "\\\\") + "\","
                    + "\"files\":" + filesJson + "}";

            handle.writer().write(request);
            handle.writer().newLine();
            handle.writer().flush();

            String response = handle.reader().readLine();
            if (response == null || response.isBlank()) {
                log.warn("[{}] daemon returned empty response", language);
                return null;
            }
            return response;

        } catch (IOException e) {
            log.warn("[{}] daemon communication error: {}", language, e.getMessage());
            daemons.remove(language);
            return null;
        } finally {
            handle.lock().unlock();
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    private void startDaemon(String language, String interpreter, String scriptName)
            throws IOException, InterruptedException {
        Path scriptPath = Path.of(scriptsBasePath, scriptName);
        if (!Files.exists(scriptPath)) {
            log.debug("[{}] script not found at {}, skipping daemon", language, scriptPath);
            return;
        }

        Process process = new ProcessBuilder(interpreter, scriptPath.toString(), "--daemon")
                .redirectErrorStream(false)
                .start();

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        // READY 시그널 대기
        String readyLine = waitForReady(reader, process);
        if (!READY_SIGNAL.equals(readyLine)) {
            process.destroyForcibly();
            log.warn("[{}] daemon did not send READY (got: '{}') — using process fallback",
                    language, readyLine);
            return;
        }

        daemons.put(language, new DaemonHandle(process, writer, reader, new ReentrantLock()));
        log.info("[{}] analysis daemon started (pid={})", language, process.pid());
    }

    private String waitForReady(BufferedReader reader, Process process)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) return null;
            try {
                if (reader.ready()) return reader.readLine();
            } catch (IOException e) {
                return null;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return null; // timeout
    }

    @Override
    public void destroy() {
        daemons.values().forEach(h -> h.process().destroyForcibly());
        daemons.clear();
        log.info("All analysis daemons stopped");
    }
}

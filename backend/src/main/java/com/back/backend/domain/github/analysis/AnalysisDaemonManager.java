package com.back.backend.domain.github.analysis;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 외부 분석 스크립트를 상주 프로세스(데몬) 풀로 관리한다.
 *
 * ※ 현재 이 클래스의 데몬 기동 기능은 사용되지 않는다.
 *
 * 데몬 방식을 채택하지 않은 이유:
 *   우리 서비스는 레포지토리 전체를 한 번에 분석하는 배치(Batch) 방식이다.
 *   - 파일 1개 분석: 인터프리터 기동(~0.5s) + 분석(~0.01s) → 기동 비용이 50배 → 데몬이 유리
 *   - 레포 전체 분석: 인터프리터 기동(~0.5s) + 파일 수백~천 개 분석(수 초) → 기동 비용 10% 미만
 *   레포 단위 배치 분석에서는 0.5초를 아끼자고 데몬 풀·헬스체크·IPC 파이프라인을
 *   유지하는 복잡도가 이득보다 크다.
 *   또한 1회성 프로세스(ProcessBuilder.waitFor)는 분석 완료 후 메모리를 즉시 OS에 반환하므로
 *   OCI 4CPU/25GB 환경에서 자원 효율이 더 좋다.
 *
 * 데몬이 필요해지는 조건 (현재는 해당 없음):
 *   - 사용자 타이핑마다 실시간 피드백이 필요한 IDE 기능 구현 시
 *   - 파일 수만 개 규모의 레포에서 변경 파일만 증분 분석할 때
 *
 * callRaw()는 항상 null을 반환하므로 StaticAnalysisService는 ProcessBuilder 경로만 사용한다.
 *
 * 프로토콜 (newline-delimited JSON, 미래 재활성화 시 참고):
 *   요청: {"repoRoot":"/path","files":["a.kt","b.kt"]}
 *   응답: [{fqn:..., file_path:..., ...}, ...]
 *   스크립트 측 시작 신호: "READY" 한 줄 출력 후 요청 대기
 */
@Service
@Deprecated
public class AnalysisDaemonManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AnalysisDaemonManager.class);

    // 언어당 데몬 수 — analysisExecutor corePoolSize(2)에 맞춤
    private static final int POOL_SIZE = 2;

    // 스크립트가 준비됐을 때 stdout으로 출력하는 시그널
    private static final String READY_SIGNAL = "READY";
    // READY 대기 최대 시간 (ms)
    private static final int STARTUP_TIMEOUT_MS = 15_000;
    // READY 폴링 간격 (ms)
    private static final int POLL_INTERVAL_MS = 100;

    // language key → [interpreter, scriptName]
    // 데몬 기동 시도용 설정 — 현재 startAll()이 스크립트 미존재로 전부 실패하므로 pools는 항상 비어 있음
    private static final Map<String, String[]> DAEMON_CONFIG = Map.of(
            "kotlin", new String[]{"python3", "kotlin_analyzer.py"},
            "python", new String[]{"python3", "python_analyzer.py"},
            "ts",     new String[]{"node",    "ts_analyzer.js"},
            "go",     new String[]{"python3", "go_analyzer.py"},
            "rust",   new String[]{"python3", "rust_analyzer.py"},
            "c",      new String[]{"python3", "c_analyzer.py"}
    );

    private final String scriptsBasePath;
    // language key → pool (기동 성공한 데몬만 포함)
    private final Map<String, BlockingQueue<DaemonHandle>> pools = new ConcurrentHashMap<>();

    private record DaemonHandle(
            Process process,
            BufferedWriter writer,
            BufferedReader reader
    ) {}

    public AnalysisDaemonManager(
            @Value("${analysis.scripts-base-path:/opt/analysis-scripts}") String scriptsBasePath
    ) {
        this.scriptsBasePath = scriptsBasePath;
    }

    @PostConstruct
    public void startAll() {
        DAEMON_CONFIG.forEach((lang, config) -> {
            BlockingQueue<DaemonHandle> pool = new ArrayBlockingQueue<>(POOL_SIZE);
            for (int i = 0; i < POOL_SIZE; i++) {
                try {
                    DaemonHandle handle = startDaemon(lang, config[0], config[1]);
                    if (handle != null) pool.add(handle);
                } catch (Exception e) {
                    log.warn("[{}] daemon #{} start failed: {}", lang, i + 1, e.getMessage());
                }
            }
            if (!pool.isEmpty()) {
                pools.put(lang, pool);
                log.info("[{}] daemon pool ready: {}/{} instances", lang, pool.size(), POOL_SIZE);
            } else {
                log.warn("[{}] no daemons started — will use process-per-request fallback", lang);
            }
        });
    }

    /**
     * 지정한 언어의 데몬 풀에서 인스턴스를 하나 꺼내 분석 요청을 전송한다.
     * 풀이 비어 있으면(모든 데몬 사용 중) 빈 인스턴스가 생길 때까지 대기한다.
     *
     * @param language  언어 키 ("kotlin", "python", "ts", "go", "rust", "c")
     * @param repoRoot  분석할 repo 루트 경로
     * @param files     분석 대상 파일 상대 경로 목록. empty → 전체 분석
     * @return 스크립트 응답 JSON 문자열, 또는 null (데몬 없거나 실패 시 → 호출자가 폴백)
     */
    public String callRaw(String language, Path repoRoot, Optional<Set<String>> files) {
        BlockingQueue<DaemonHandle> pool = pools.get(language);
        if (pool == null || pool.isEmpty()) return null;

        DaemonHandle handle;
        try {
            handle = pool.take(); // 빈 데몬이 생길 때까지 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        // 데몬 죽었으면 풀에 돌려놓지 않고 폴백
        if (!handle.process().isAlive()) {
            log.warn("[{}] daemon died — removed from pool, using process fallback", language);
            return null;
        }

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
            return null; // 죽은 데몬이므로 풀에 반납하지 않음
        } finally {
            // 살아있는 데몬만 풀에 반납
            if (handle.process().isAlive()) {
                pool.offer(handle);
            }
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    /** 데몬 1개를 기동하고 READY 신호 확인 후 반환. 실패 시 null. */
    private DaemonHandle startDaemon(String language, String interpreter, String scriptName)
            throws IOException, InterruptedException {
        Path scriptPath = Path.of(scriptsBasePath, scriptName);
        if (!Files.exists(scriptPath)) {
            log.debug("[{}] script not found at {}, skipping daemon", language, scriptPath);
            return null;
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
            return null;
        }

        log.info("[{}] analysis daemon started (pid={})", language, process.pid());
        return new DaemonHandle(process, writer, reader);
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
        pools.values().forEach(pool -> {
            List<DaemonHandle> handles = new ArrayList<>();
            pool.drainTo(handles);
            handles.forEach(h -> h.process().destroyForcibly());
        });
        pools.clear();
        log.info("All analysis daemons stopped");
    }
}

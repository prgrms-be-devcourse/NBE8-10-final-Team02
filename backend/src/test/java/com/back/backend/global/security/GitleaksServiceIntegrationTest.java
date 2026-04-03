package com.back.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GitleaksService 통합 테스트 — gitleaks 바이너리가 실제로 설치된 환경에서만 실행된다.
 *
 * <p>gitleaks 미설치 시 모든 테스트가 자동으로 skip된다.</p>
 */
class GitleaksServiceIntegrationTest {

    private static boolean gitleaksAvailable;

    private static String gitleaksBinary;

    @BeforeAll
    static void checkGitleaksAvailable() {
        // Windows cmd, Unix which 순으로 시도
        for (String candidate : new java.util.ArrayList<>(java.util.Arrays.asList("gitleaks", resolveFromWhere()))) {
            if (candidate == null) continue;
            try {
                Process p = new ProcessBuilder(candidate, "version")
                        .redirectErrorStream(true)
                        .start();
                p.waitFor();
                if (p.exitValue() == 0) {
                    gitleaksBinary = candidate;
                    gitleaksAvailable = true;
                    return;
                }
            } catch (Exception ignored) {}
        }
        gitleaksAvailable = false;
    }

    /** Windows `where gitleaks` 또는 Unix `which gitleaks` 로 전체 경로 조회 */
    private static String resolveFromWhere() {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> cmd = os.contains("win")
                ? List.of("cmd.exe", "/c", "where", "gitleaks")
                : List.of("which", "gitleaks");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            // where는 여러 줄 반환 가능 — 첫 번째 줄만 사용
            String first = out.lines().findFirst().orElse("").trim();
            return first.isEmpty() ? null : first;
        } catch (Exception e) {
            return null;
        }
    }

    private GitleaksService buildService() {
        return new GitleaksService(gitleaksBinary, 30, true, new ObjectMapper());
    }

    /** 진단용 — 항상 실행되며 PATH/OS/바이너리 탐색 결과를 출력한다. */
    @Test
    void debug_printEnvironment() {
        System.out.println("=== Gitleaks 환경 진단 ===");
        System.out.println("os.name  : " + System.getProperty("os.name"));
        System.out.println("os.arch  : " + System.getProperty("os.arch"));
        System.out.println("PATH     : " + System.getenv("PATH"));
        System.out.println("resolved : " + resolveFromWhere());
        System.out.println("available: " + gitleaksAvailable);
        System.out.println("binary   : " + gitleaksBinary);

        // resolveFromWhere() 전체 출력 (첫 줄 잘리기 전)
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> cmd = os.contains("win")
                ? List.of("cmd.exe", "/c", "where", "gitleaks")
                : List.of("which", "gitleaks");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            System.out.println("where raw: [" + out + "] exit=" + p.exitValue());
        } catch (Exception e) {
            System.out.println("where err: " + e);
        }
        // 바이너리 직접 실행 시도
        for (String bin : new String[]{"gitleaks", "gitleaks.exe"}) {
            try {
                Process p = new ProcessBuilder(bin, "version").redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor();
                System.out.println("run [" + bin + "]: exit=" + p.exitValue() + " out=" + out);
            } catch (Exception e) {
                System.out.println("run [" + bin + "]: ERR " + e.getMessage());
            }
        }
        System.out.println("=========================");
    }

    @Test
    void scanTextForMasking_detectsGithubPat() {
        assumeTrue(gitleaksAvailable, "gitleaks binary not found — skipping");

        GitleaksService sut = buildService();
        // 형식이 맞는 GitHub PAT (실제 유효 토큰 아님)
        String text = "KNOWLEDGE_GITHUB_TOKEN=ghp_AAAAAABBBBBBCCCCCCDDDDDDEEEEEE123456";

        List<String> secrets = sut.scanTextForMasking(text);

        assertThat(secrets).isNotEmpty();
        // 실제 값이 포함되어 있어야 마스킹이 가능함
        assertThat(secrets).anyMatch(s -> text.contains(s));
    }

    @Test
    void scanTextForMasking_returnsEmptyForPlainText() {
        assumeTrue(gitleaksAvailable, "gitleaks binary not found — skipping");

        GitleaksService sut = buildService();
        String text = "이름: 홍길동\n직업: 개발자\n경력: 3년";

        List<String> secrets = sut.scanTextForMasking(text);

        assertThat(secrets).isEmpty();
    }

    @Test
    void scanRepo_returnsEmptyForCleanDirectory() {
        assumeTrue(gitleaksAvailable, "gitleaks binary not found — skipping");

        GitleaksService sut = buildService();
        java.nio.file.Path tmp;
        try {
            tmp = java.nio.file.Files.createTempDirectory("gitleaks-test-clean-");
            java.nio.file.Files.writeString(tmp.resolve("readme.txt"), "hello world");
        } catch (Exception e) {
            assumeTrue(false, "Failed to create temp dir: " + e.getMessage());
            return;
        }

        GitleaksService.GitleaksScanResult result = sut.scanRepo(tmp);

        assertThat(result.hasFindings()).isFalse();
    }
}

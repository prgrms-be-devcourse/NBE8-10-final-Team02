package com.back.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitleaksServiceTest {

    private GitleaksService service;
    private Path fakeBinary;

    @BeforeEach
    void setUp() throws Exception {
        fakeBinary = createFakeBinary();
        service = new GitleaksService(fakeBinary.toString(), 1, true, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void scanRepo_returnsEmptyWhenNoFindingsExist() throws Exception {
        Path repoDir = Files.createTempDirectory("gitleaks-no-findings");

        GitleaksService.GitleaksScanResult result = service.scanRepo(repoDir);

        assertThat(result.hasFindings()).isFalse();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void scanRepo_returnsSanitizedFindingsWithoutSecretValue() throws Exception {
        Path repoDir = Files.createTempDirectory("gitleaks-findings");
        Files.createFile(repoDir.resolve("mode-finding"));

        GitleaksService.GitleaksScanResult result = service.scanRepo(repoDir);

        assertThat(result.hasFindings()).isTrue();
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.filePath()).isEqualTo("src/Secret.java");
            assertThat(finding.ruleId()).isEqualTo("generic-api-key");
            assertThat(finding.description()).isEqualTo("Hardcoded secret");
            assertThat(finding.toString()).doesNotContain("ghp_real_secret");
        });
    }

    @Test
    void scanRepo_returnsEmptyWhenCliExitsWithError() throws Exception {
        Path repoDir = Files.createTempDirectory("gitleaks-error");
        Files.createFile(repoDir.resolve("mode-error"));

        GitleaksService.GitleaksScanResult result = service.scanRepo(repoDir);

        assertThat(result.hasFindings()).isFalse();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void scanRepo_returnsEmptyWhenReportIsBlank() throws Exception {
        Path repoDir = Files.createTempDirectory("gitleaks-empty-report");
        Files.createFile(repoDir.resolve("mode-empty-report"));

        GitleaksService.GitleaksScanResult result = service.scanRepo(repoDir);

        assertThat(result.hasFindings()).isFalse();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void scanRepo_returnsEmptyWhenProcessTimesOut() throws Exception {
        Path repoDir = Files.createTempDirectory("gitleaks-timeout");
        Files.createFile(repoDir.resolve("mode-timeout"));

        GitleaksService.GitleaksScanResult result = service.scanRepo(repoDir);

        assertThat(result.hasFindings()).isFalse();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void scanTextForMasking_returnsMatchedSecretValuesOnly() {
        List<String> secrets = service.scanTextForMasking("prefix LEAK_ME suffix");

        assertThat(secrets).containsExactly("LEAK_ME");
    }

    private Path createFakeBinary() throws Exception {
        Path script = Files.createTempFile("fake-gitleaks-", ".cmd");
        String content = """
                @echo off
                setlocal EnableDelayedExpansion
                set "SOURCE="
                set "REPORT="
                :parse
                if "%~1"=="" goto afterParse
                if "%~1"=="--source" (
                  set "SOURCE=%~2"
                  shift
                ) else if "%~1"=="--report-path" (
                  set "REPORT=%~2"
                  shift
                )
                shift
                goto parse
                :afterParse
                if exist "%SOURCE%\\mode-timeout" (
                  powershell -NoProfile -Command "Start-Sleep -Seconds 2" >nul
                  exit /b 0
                )
                if exist "%SOURCE%\\mode-error" exit /b 2
                if exist "%SOURCE%\\mode-empty-report" (
                  type nul > "%REPORT%"
                  exit /b 1
                )
                if exist "%SOURCE%\\mode-finding" (
                  > "%REPORT%" echo [{"File":"src/Secret.java","RuleID":"generic-api-key","Description":"Hardcoded secret","Secret":"ghp_real_secret"}]
                  exit /b 1
                )
                if exist "%SOURCE%\\content.txt" (
                  findstr /C:"LEAK_ME" "%SOURCE%\\content.txt" >nul
                  if not errorlevel 1 (
                    > "%REPORT%" echo [{"RuleID":"generic-api-key","Secret":"LEAK_ME"}]
                    exit /b 1
                  )
                )
                exit /b 0
                """;
        Files.writeString(script, content.replace("\n", "\r\n"));
        return script;
    }
}

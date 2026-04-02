package com.back.backend.domain.github.service;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * GithubApiClient.revokeAccessToken() 단위 테스트.
 *
 * WireMock으로 GitHub API를 stub하여 DELETE /applications/{client_id}/token 호출을
 * 올바른 인증(Basic auth)과 body로 전송하는지 검증한다.
 * Spring 컨텍스트 없이 GithubApiClient를 직접 생성해 실행한다.
 */
class GithubApiClientRevokeTokenTest {

    static final String TEST_CLIENT_ID = "test-client-id";
    static final String TEST_CLIENT_SECRET = "test-client-secret";
    //noinspection JsonStandardCompliance
    static final String TEST_TOKEN = "gho_testAccessToken123"; //에러나도 정상임.

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private GithubApiClient client;

    @BeforeEach
    void setUp() {
        client = new GithubApiClient(
                wireMock.baseUrl(),
                TEST_CLIENT_ID,
                TEST_CLIENT_SECRET
        );
    }

    // ─────────────────────────────────────────────────
    // 정상 동작
    // ─────────────────────────────────────────────────

    @Test
    void revokeAccessToken_sendsDeleteWithBasicAuthAndToken() {
        wireMock.stubFor(delete(urlEqualTo("/applications/" + TEST_CLIENT_ID + "/grant"))
                .willReturn(aResponse().withStatus(204)));

        client.revokeAccessToken(TEST_TOKEN);

        // Basic auth 헤더 검증: base64(client_id:client_secret)
        String expectedBasic = "Basic " + java.util.Base64.getEncoder()
                .encodeToString((TEST_CLIENT_ID + ":" + TEST_CLIENT_SECRET).getBytes());

        wireMock.verify(deleteRequestedFor(urlEqualTo("/applications/" + TEST_CLIENT_ID + "/grant"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(expectedBasic))
                .withRequestBody(equalToJson("{\"access_token\": \"" + TEST_TOKEN + "\"}")));
    }

    @Test
    void revokeAccessToken_doesNotThrowWhenGithubReturns404() {
        // token이 이미 만료된 경우 GitHub은 404를 반환할 수 있다
        wireMock.stubFor(delete(urlEqualTo("/applications/" + TEST_CLIENT_ID + "/grant"))
                .willReturn(aResponse().withStatus(404)));

        assertThatCode(() -> client.revokeAccessToken(TEST_TOKEN))
                .doesNotThrowAnyException();
    }

    @Test
    void revokeAccessToken_doesNotThrowWhenGithubReturns500() {
        wireMock.stubFor(delete(urlEqualTo("/applications/" + TEST_CLIENT_ID + "/grant"))
                .willReturn(aResponse().withStatus(500)));

        assertThatCode(() -> client.revokeAccessToken(TEST_TOKEN))
                .doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────
    // Guard 조건 — GitHub API를 호출하지 않아야 하는 경우
    // ─────────────────────────────────────────────────

    @Test
    void revokeAccessToken_isNoOpWhenTokenIsNull() {
        client.revokeAccessToken(null);

        wireMock.verify(0, deleteRequestedFor(urlEqualTo("/applications/" + TEST_CLIENT_ID + "/grant")));
    }

    @Test
    void revokeAccessToken_isNoOpWhenTokenIsBlank() {
        client.revokeAccessToken("   ");

        wireMock.verify(0, deleteRequestedFor(urlEqualTo("/applications/" + TEST_CLIENT_ID + "/grant")));
    }

    @Test
    void revokeAccessToken_isNoOpWhenClientCredentialsNotConfigured() {
        GithubApiClient clientWithoutCreds = new GithubApiClient(wireMock.baseUrl(), "", "");

        clientWithoutCreds.revokeAccessToken(TEST_TOKEN);

        wireMock.verify(0, deleteRequestedFor(urlEqualTo("/applications/" + TEST_CLIENT_ID + "/grant")));
    }
}

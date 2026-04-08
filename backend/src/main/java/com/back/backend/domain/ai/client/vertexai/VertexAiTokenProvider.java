package com.back.backend.domain.ai.client.vertexai;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Vertex AI용 OAuth2 Access Token 관리
 * GCP Service Account JSON 파일에서 GoogleCredentials를 생성하고
 * 토큰 만료 시 자동으로 갱신하여 유효한 Access Token을 반환
 */
public class VertexAiTokenProvider {

    private static final List<String> SCOPES = List.of(
        "https://www.googleapis.com/auth/cloud-platform"
    );

    private final GoogleCredentials credentials;

    /**
     * @param credentialsPath GCP Service Account JSON 키 파일의 절대 경로
     * @throws IOException 키 파일 읽기 실패 시
     */
    public VertexAiTokenProvider(String credentialsPath) throws IOException {
        try (var stream = new FileInputStream(credentialsPath)) {
            this.credentials = GoogleCredentials.fromStream(stream)
                .createScoped(SCOPES);
        }
    }

    /**
     * 유효한 Access Token을 반환
     * GoogleCredentials가 내부적으로 토큰 만료 5분 전 자동 갱신을 처리
     *
     * @return OAuth2 Access Token 문자열
     * @throws IOException 토큰 갱신 네트워크 오류 시
     */
    public String getAccessToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}

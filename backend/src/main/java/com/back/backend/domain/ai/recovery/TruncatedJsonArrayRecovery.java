package com.back.backend.domain.ai.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 토큰 한도 초과로 절단된 JSON 배열에서 완성된 원소만 추출한다.
 *
 * <h3>동작 원리</h3>
 * AI가 출력 토큰이 소진되면 JSON 배열이 중간에 잘린다. 예:
 * <pre>
 * [
 *   {"repoId": "a", "project": {...완성...}},
 *   {"repoId": "b", "project": {"key": "val   ← 여기서 잘림
 * </pre>
 *
 * 이 클래스는 문자열 끝에서부터 역방향으로 {@code }} 위치를 탐색하여,
 * 각 후보 위치에 {@code ]} 을 붙인 문자열 파싱을 시도한다.
 * 파싱에 성공하고 배열 원소가 1개 이상이면 해당 배열을 반환한다.
 *
 * <h3>성능 특성</h3>
 * 최악의 경우(모두 실패) 전체 문자열 길이만큼 탐색하나,
 * 실제로는 마지막 완성 원소의 닫는 {@code }} 에서 수 번 내에 성공한다.
 */
public class TruncatedJsonArrayRecovery {

    private static final Logger log = LoggerFactory.getLogger(TruncatedJsonArrayRecovery.class);

    private final ObjectMapper objectMapper;

    public TruncatedJsonArrayRecovery(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 절단된 JSON 배열 문자열에서 완성된 원소만 추출한다.
     *
     * @param truncatedJson 절단되었을 수 있는 JSON 문자열
     * @return 복구된 JsonNode(배열, size ≥ 1), 복구 불가면 empty
     */
    public Optional<JsonNode> tryRecover(String truncatedJson) {
        if (truncatedJson == null || truncatedJson.isBlank()) {
            return Optional.empty();
        }

        String trimmed = truncatedJson.stripLeading();
        if (!trimmed.startsWith("[")) {
            // 배열 형태가 아니면 복구 대상 아님
            return Optional.empty();
        }

        // 끝에서부터 역방향으로 '}' 를 탐색 — 배열 원소가 닫힌 지점 후보
        for (int i = truncatedJson.length() - 1; i >= 0; i--) {
            if (truncatedJson.charAt(i) != '}') {
                continue;
            }

            String candidate = truncatedJson.substring(0, i + 1) + "]";
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (node.isArray() && !node.isEmpty()) {
                    log.debug("TruncatedJsonArrayRecovery: recovered {} element(s) from truncated JSON (cutAt={})",
                            node.size(), i + 1);
                    return Optional.of(node);
                }
            } catch (Exception ignored) {
                // 파싱 실패 → 다음 후보로 계속 탐색
            }
        }

        return Optional.empty();
    }
}

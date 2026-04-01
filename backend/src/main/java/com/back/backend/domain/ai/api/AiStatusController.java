package com.back.backend.domain.ai.api;

import com.back.backend.domain.ai.usage.AiStatusService;
import com.back.backend.domain.ai.usage.dto.AiStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 서비스 가용성 상태 조회 컨트롤러
 * 인증 없이 접근 가능 (SecurityConfig에서 permitAll 설정)
 * 클라이언트가 AI 기능 호출 전 가용성을 미리 확인하거나
 * 사용자에게 현재 AI 서비스 상태를 안내하는 데 사용
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiStatusController {

    private final AiStatusService aiStatusService;

    public AiStatusController(AiStatusService aiStatusService) {
        this.aiStatusService = aiStatusService;
    }

    /**
     * AI 서비스 가용성 상태 조회
     * 각 provider의 RPM/TPM/RPD/TPD 사용량과 AVAILABLE/MINUTE_RATE_LIMITED/DAILY_EXHAUSTED 상태 반환
     *
     * @return AI 서비스 전체 가용성 및 provider별 상세 상태
     */
    @GetMapping("/status")
    public AiStatusResponse getStatus() {
        return aiStatusService.getStatus();
    }
}

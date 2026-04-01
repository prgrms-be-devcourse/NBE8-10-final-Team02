CREATE TABLE ai_provider_usage (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider          VARCHAR(20) NOT NULL COMMENT 'AI provider 코드 (gemini, groq)',
    usage_date        DATE        NOT NULL COMMENT '사용 날짜 (UTC 기준)',
    request_count     INT         NOT NULL DEFAULT 0 COMMENT '성공 요청 수',
    prompt_tokens     BIGINT      NOT NULL DEFAULT 0 COMMENT '입력 토큰 누적',
    completion_tokens BIGINT      NOT NULL DEFAULT 0 COMMENT '출력 토큰 누적',
    total_tokens      BIGINT      NOT NULL DEFAULT 0 COMMENT '전체 토큰 누적',
    rate_limit_hits   INT         NOT NULL DEFAULT 0 COMMENT '429 발생 횟수',
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_provider_date (provider, usage_date)
) COMMENT = 'AI provider 일간 사용량 누적';

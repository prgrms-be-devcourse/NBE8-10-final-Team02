-- 1. 테이블 생성
CREATE TABLE ai_provider_usage (
                                 id                BIGSERIAL PRIMARY KEY,
                                 provider          VARCHAR(20) NOT NULL,
                                 usage_date        DATE        NOT NULL,
                                 request_count     INT         NOT NULL DEFAULT 0,
                                 prompt_tokens     BIGINT      NOT NULL DEFAULT 0,
                                 completion_tokens BIGINT      NOT NULL DEFAULT 0,
                                 total_tokens      BIGINT      NOT NULL DEFAULT 0,
                                 rate_limit_hits   INT         NOT NULL DEFAULT 0,
                                 created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT uq_provider_date UNIQUE (provider, usage_date)
);

-- 2. 테이블 및 컬럼 주석 추가 (PostgreSQL은 별도 명령어로 추가해야 함)
COMMENT ON TABLE ai_provider_usage IS 'AI provider 일간 사용량 누적';
COMMENT ON COLUMN ai_provider_usage.provider IS 'AI provider 코드 (gemini, groq)';
COMMENT ON COLUMN ai_provider_usage.usage_date IS '사용 날짜 (UTC 기준)';
COMMENT ON COLUMN ai_provider_usage.request_count IS '성공 요청 수';
COMMENT ON COLUMN ai_provider_usage.prompt_tokens IS '입력 토큰 누적';
COMMENT ON COLUMN ai_provider_usage.completion_tokens IS '출력 토큰 누적';
COMMENT ON COLUMN ai_provider_usage.total_tokens IS '전체 토큰 누적';
COMMENT ON COLUMN ai_provider_usage.rate_limit_hits IS '429 발생 횟수';

-- 3. updated_at 자동 갱신 트리거 생성 (MySQL의 ON UPDATE CURRENT_TIMESTAMP 대체)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ai_provider_usage_updated_at
  BEFORE UPDATE ON ai_provider_usage
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

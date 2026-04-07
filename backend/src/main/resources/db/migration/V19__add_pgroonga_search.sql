-- ============================================================
-- V19: PGroonga 전문 검색(FTS) 인덱스 추가
--
-- PGroonga는 PostgreSQL용 고성능 전문 검색 확장이다.
-- &@~ 연산자로 Fuzzy search / FTS / 한국어 형태소 분석을 지원한다.
--
-- 환경별 동작:
--   - PGroonga 설치된 환경(Docker: groonga/pgroonga:*): 확장 및 인덱스 생성
--   - 일반 PostgreSQL(로컬 개발, 기존 CI): 경고만 출력하고 마이그레이션 통과
--     → searchByText/searchByFileName API는 이 환경에서 오류를 반환할 수 있음
-- ============================================================

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pgroonga;
    RAISE NOTICE 'PGroonga extension created/already exists.';
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'PGroonga extension not available: %. FTS indexes will not be created.', SQLERRM;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgroonga') THEN
        -- extracted_text 전문 검색 인덱스
        -- pgroonga_text_full_text_search_ops_v2: 한국어/영어 FTS + Fuzzy search 지원
        EXECUTE 'CREATE INDEX IF NOT EXISTS pgroonga_documents_extracted_text
                 ON documents USING pgroonga (extracted_text pgroonga_text_full_text_search_ops_v2)';

        -- 파일명 검색 인덱스
        EXECUTE 'CREATE INDEX IF NOT EXISTS pgroonga_documents_filename
                 ON documents USING pgroonga (original_file_name pgroonga_text_full_text_search_ops_v2)';

        RAISE NOTICE 'PGroonga FTS indexes created on documents table.';
    ELSE
        RAISE WARNING 'PGroonga not enabled. Run with groonga/pgroonga image for FTS support.';
    END IF;
END $$;

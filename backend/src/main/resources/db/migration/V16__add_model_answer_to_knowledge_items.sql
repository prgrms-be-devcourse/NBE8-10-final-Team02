-- 모범답안 캐싱: 첫 평가 시 생성된 모범답안을 저장하여 이후 재사용
ALTER TABLE knowledge_items ADD COLUMN model_answer text;

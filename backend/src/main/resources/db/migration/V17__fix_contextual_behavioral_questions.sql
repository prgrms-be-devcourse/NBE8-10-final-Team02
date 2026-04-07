-- 맥락 의존 인성 질문을 자체 완결적으로 수정
-- "그 경험", "그 실패", "반대로" 등으로 시작하는 질문에 원래 맥락을 포함

-- B_008 [pressure]: "그 경험에서..." → "가장 자랑스러운 경험에서..."
UPDATE knowledge_items
SET content = '본인이 가장 자랑스럽게 생각하는 경험에서, 본인의 역할이 없었다면 결과가 달라졌을까요?

[AI Guide]
STAR 구조(상황-과제-행동-결과)로 서술하도록 유도한다.',
    content_hash = encode(sha256(convert_to('본인이 가장 자랑스럽게 생각하는 경험에서, 본인의 역할이 없었다면 결과가 달라졌을까요?

[AI Guide]
STAR 구조(상황-과제-행동-결과)로 서술하도록 유도한다.', 'UTF8')), 'hex'),
    updated_at = current_timestamp
WHERE title = 'B_008 [pressure]' AND source_key = 'local-behavioral';

-- B_010 [pressure]: "그 실패가..." → "실패 경험이..."
UPDATE knowledge_items
SET content = '실패 경험이 팀 전체에 영향을 미쳤다면 어떻게 책임졌을 것 같나요?

[AI Guide]
실패를 숨기지 않고 학습과 성장의 맥락에서 서술하도록 유도한다.',
    content_hash = encode(sha256(convert_to('실패 경험이 팀 전체에 영향을 미쳤다면 어떻게 책임졌을 것 같나요?

[AI Guide]
실패를 숨기지 않고 학습과 성장의 맥락에서 서술하도록 유도한다.', 'UTF8')), 'hex'),
    updated_at = current_timestamp
WHERE title = 'B_010 [pressure]' AND source_key = 'local-behavioral';

-- C_002 [pressure]: "반대로 본인이..." → "본인이..." (반대로 제거하고 자체 완결적으로)
UPDATE knowledge_items
SET content = '본인이 동료의 코드나 태도에 대해 아주 강한 비판을 전달해야 했던 적이 있나요? 상대방이 기분 상하지 않게 어떻게 전달하셨나요?

[AI Guide]
지원자가 작성한 코드 중 최적화가 덜 되었거나 복잡해 보이는 부분을 짚으며, ''이 코드를 동료가 강하게 비판했다면 어떻게 방어할 것인가?''라고 물어볼 것.',
    content_hash = encode(sha256(convert_to('본인이 동료의 코드나 태도에 대해 아주 강한 비판을 전달해야 했던 적이 있나요? 상대방이 기분 상하지 않게 어떻게 전달하셨나요?

[AI Guide]
지원자가 작성한 코드 중 최적화가 덜 되었거나 복잡해 보이는 부분을 짚으며, ''이 코드를 동료가 강하게 비판했다면 어떻게 방어할 것인가?''라고 물어볼 것.', 'UTF8')), 'hex'),
    updated_at = current_timestamp
WHERE title = 'C_002 [pressure]' AND source_key = 'local-behavioral';

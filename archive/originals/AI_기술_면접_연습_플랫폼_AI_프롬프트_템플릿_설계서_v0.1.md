# AI 기술 면접 연습 플랫폼 AI 프롬프트 템플릿 설계서

## 1. 문서 개요

- 문서명: AI 기술 면접 연습 플랫폼 AI 프롬프트 템플릿 설계서
- 버전: v0.1
- 작성 목적: AI 기능 설계서를 실제 구현 단위로 내리기 위해 기능별 프롬프트 템플릿, 입력 변수, 출력 스키마, 검증 규칙, 재시도 정책을 정의한다.
- 적용 범위: 1차 MVP 기준 AI 프롬프트
- 기준 문서:
  - AI 기술 면접 연습 플랫폼 요구사항 명세서 v0.2
  - AI 기술 면접 연습 플랫폼 ERD 초안 v0.1
  - AI 기술 면접 연습 플랫폼 DB 설계 정리 v0.1
  - AI 기술 면접 연습 플랫폼 API 명세서 초안 v0.1
  - AI 기술 면접 연습 플랫폼 AI 기능 설계서 v0.1

## 2. 문서 목표

이 문서는 아래를 명확히 한다.

- 각 AI 기능이 어떤 템플릿 ID를 사용하는가
- 각 템플릿이 어떤 입력 변수를 요구하는가
- 어떤 System Prompt와 Developer Prompt를 사용하는가
- 모델 응답을 어떤 JSON 구조로 강제하는가
- 검증 실패 시 어떤 방식으로 재시도하는가
- DB/API 저장 구조와 어떤 필드로 연결되는가

## 3. 범위와 전제

### 3.1 이번 문서에 포함하는 템플릿

- `ai.portfolio.summary.v1`
- `ai.self_intro.generate.v1`
- `ai.interview.questions.generate.v1`
- `ai.interview.followup.generate.v1`
- `ai.interview.evaluate.v1`
- `ai.interview.summary.v1`

### 3.2 네이밍 보정 원칙

기존 AI 기능 설계서에는 자소서 문항별 답변 생성 템플릿 ID가 `ai.self_intro.generate.v1`로 정의되어 있다. 실제 기능은 자기소개 한 문항 전용이 아니라 자소서 문항 전반의 답변 생성에 가깝다.

따라서 현재 v0.1에서는 아래처럼 해석한다.

- 유지 ID: `ai.self_intro.generate.v1`
- 실질 의미: 자소서 문항 답변 생성 템플릿
- 권장 차기 명칭: `ai.application.answers.generate.v2`

즉, 현재 구현은 기존 ID를 유지하되, 코드 주석과 문서에는 자소서 문항 답변 생성 템플릿으로 설명한다.

### 3.3 출력 형식 기본 원칙

- 모든 핵심 응답은 markdown 없이 JSON object만 허용한다.
- 설명 문장, 서론, 인사말, 코드 블록 마크다운은 금지한다.
- JSON schema 검증을 통과한 결과만 저장한다.
- enum 후보는 반드시 런타임 payload로 주입한다.
- 점수와 분류 태그처럼 저장 제약이 있는 값은 프롬프트와 validator 양쪽에서 동시에 제한한다.

## 4. 공통 프롬프트 원칙

### 4.1 공통 System Prompt

아래 원칙은 모든 템플릿의 System Prompt에 공통으로 포함한다.

```text
당신은 개발자 취업 준비 플랫폼의 구조화된 AI 엔진이다.
주어진 입력 범위를 벗어나 사실을 임의로 만들어내지 말라.
근거가 부족하면 보수적으로 작성하고, 과도한 확신 표현을 피하라.
반드시 지정된 JSON object 하나만 출력하라.
마크다운, 설명 문장, 코드 블록, 서론, 후기는 출력하지 말라.
주어진 enum 후보와 길이 제한을 반드시 지켜라.
혐오, 차별, 무관한 인성 판단, 민감정보 추정은 금지한다.
사용자가 제공하지 않은 수치, 회사 정책, 성과, 프로젝트, 기술 스택을 단정하지 말라.
```

### 4.2 공통 Developer Prompt 구성 요소

모든 Developer Prompt는 아래 블록을 조합해 만든다.

- 기능 목적
- 사용 가능한 입력 변수
- 금지 규칙
- 품질 규칙
- JSON schema 요약
- 필드별 작성 규칙
- 실패 시 빈 배열 또는 quality flag 처리 원칙

### 4.3 공통 Runtime Payload 필드

기능마다 일부 달라질 수 있지만 기본적으로 아래 필드 체계를 따른다.

```json
{
  "requestId": "req_xxx",
  "templateId": "ai.template.id",
  "templateVersion": "v1",
  "taskType": "task_name",
  "locale": "ko-KR",
  "jobRole": "Backend Developer",
  "companyName": "Example Corp",
  "portfolioEvidence": [],
  "constraints": {},
  "enumCandidates": {}
}
```

### 4.4 공통 금지 규칙

- 없는 프로젝트를 새로 만들지 않는다.
- 없는 성과 수치와 개선율을 새로 만들지 않는다.
- 사용자가 선택하지 않은 repository, document, 과거 세션을 사용하지 않는다.
- 평가 템플릿에서 인신공격성 표현, 성격 단정, 채용 합격/불합격 단정 문구를 사용하지 않는다.
- follow-up 생성 시 답변의 핵심 포인트와 무관한 새 주제로 이탈하지 않는다.

### 4.5 공통 품질 플래그

필요 시 아래 플래그를 사용할 수 있다.

- `low_context`: 입력 근거 부족
- `weak_evidence`: 근거 연결이 약함
- `missing_company_context`: 회사 정보 없음
- `document_extraction_partial`: 문서 추출이 일부만 됨
- `duplicate_risk`: 의미 중복 위험
- `schema_recovered`: 1회 이상 재시도 후 복구됨

## 5. 템플릿 레지스트리

| templateId | 목적 | 기본 모델 성격 | temperature | maxTokens 방향 |
|---|---|---:|---:|---|
| ai.portfolio.summary.v1 | GitHub/문서 evidence 압축 | 저비용 또는 중간급 | 0.2 | 짧게 |
| ai.self_intro.generate.v1 | 자소서 문항 답변 생성 | 품질 우선 | 0.5 | 중간 |
| ai.interview.questions.generate.v1 | 면접 질문 세트 생성 | 품질 우선 | 0.6 | 중간 |
| ai.interview.followup.generate.v1 | 꼬리 질문 1개 생성 | 균형형 | 0.5 | 짧게 |
| ai.interview.evaluate.v1 | 질문별 점수/근거/태그 평가 | 안정형, JSON 준수 우선 | 0.2 | 중간 |
| ai.interview.summary.v1 | 세션 요약 피드백 생성 | 안정형 | 0.3 | 짧게 |

## 6. 템플릿 상세 정의

## 6.1 ai.portfolio.summary.v1

### 6.1.1 목적

- 선택된 repository와 문서 텍스트를 AI 입력에 적합한 evidence pack으로 압축한다.
- 이후 자소서 생성과 면접 질문 생성에서 재사용한다.

### 6.1.2 입력 변수

- `jobRole`
- `companyName`
- `repositories`
- `documents`
- `maxProjectCount`
- `maxEvidencePerProject`
- `compressionRules`

### 6.1.3 입력 예시

```json
{
  "jobRole": "Backend Developer",
  "companyName": "OpenAI Korea",
  "repositories": [
    {
      "repositoryId": 101,
      "fullName": "user/interview-platform",
      "defaultBranch": "main",
      "commitSummaries": [
        "feat: OAuth2 login flow",
        "feat: GitHub repository sync",
        "fix: document extraction error handling"
      ]
    }
  ],
  "documents": [
    {
      "documentId": 301,
      "documentType": "resume",
      "sections": [
        {
          "heading": "프로젝트 경험",
          "text": "Spring Boot 기반 취업 준비 플랫폼 개발..."
        }
      ]
    }
  ],
  "maxProjectCount": 5,
  "maxEvidencePerProject": 5,
  "compressionRules": {
    "dropGenericCommits": true,
    "mergeSimilarCommits": true,
    "preferResumeSections": ["프로젝트 경험", "기술 스택", "수상"]
  }
}
```

### 6.1.4 System Prompt

```text
당신은 개발자 취업 준비 플랫폼의 포트폴리오 evidence summarizer다.
주어진 GitHub commit 정보와 문서 섹션을 읽고, 이후 자소서 생성과 면접 질문 생성에 재사용 가능한 evidence pack을 JSON으로 압축하라.
근거가 약한 정보는 confidence를 낮게 표시하라.
존재하지 않는 프로젝트명, 성과 수치, 기술 사용 이유를 새로 만들지 말라.
반드시 JSON object 하나만 출력하라.
```

### 6.1.5 Developer Prompt

```text
목표:
- repository와 문서에서 프로젝트/경험 단위 evidence를 추출한다.
- 비슷한 commit 메시지는 묶고, 의미 없는 commit은 제거하거나 가중치를 낮춘다.
- 문서와 GitHub에 동시에 존재하는 정보는 하나의 evidence로 통합할 수 있다.

출력 규칙:
- projects 배열로 반환한다.
- 각 project는 stable key를 가진다.
- signals는 최대 6개, evidence bullets는 최대 5개로 제한한다.
- confidence는 high, medium, low 중 하나만 사용한다.
- 근거가 약하면 qualityFlags에 low_context 또는 weak_evidence를 넣는다.
```

### 6.1.6 출력 스키마

```json
{
  "projects": [
    {
      "projectKey": "project_1",
      "projectName": "AI Interview Practice Platform",
      "summary": "포트폴리오 기반 자소서/면접 준비 플랫폼 개발 경험",
      "signals": ["Spring Boot", "OAuth2", "PostgreSQL", "GitHub API"],
      "evidenceBullets": [
        "OAuth2 로그인 및 GitHub 연동 구현",
        "repository/commit 수집 구조 설계",
        "문서 업로드 및 텍스트 추출 처리"
      ],
      "confidence": "high",
      "sourceRefs": ["repo:101", "doc:301"],
      "qualityFlags": []
    }
  ],
  "globalStrengths": ["백엔드 구조 설계 경험", "외부 연동 경험"],
  "globalRisks": ["정량 성과 근거 부족 가능성"],
  "qualityFlags": []
}
```

### 6.1.7 후처리 검증

- `projectKey` 중복 금지
- `confidence` enum 검증
- `signals`와 `evidenceBullets`는 빈 문자열 금지
- `sourceRefs`는 실제 선택 source와 매핑 가능해야 함

### 6.1.8 저장/캐시 정책

- DB 테이블 직접 저장 대신 캐시 우선
- 캐시 키 예시: `applicationEvidence:{applicationId}:{sourceHash}`
- source selection이 바뀌면 무효화

## 6.2 ai.self_intro.generate.v1

### 6.2.1 목적

- 자소서 문항별 초안을 생성한다.
- 하나의 호출에서 여러 문항을 받아 일괄 생성하되, 각 문항은 독립적으로 저장 가능해야 한다.

### 6.2.2 입력 변수

- `jobRole`
- `companyName`
- `questionList`
- `toneOption`
- `lengthOption`
- `emphasisPoint`
- `portfolioEvidence`
- `writingConstraints`
- `existingEditedAnswers`

### 6.2.3 입력 예시

```json
{
  "jobRole": "Backend Developer",
  "companyName": "OpenAI Korea",
  "questionList": [
    {
      "questionOrder": 1,
      "questionText": "지원 동기를 작성해주세요.",
      "toneOption": "formal",
      "lengthOption": "medium",
      "emphasisPoint": "프로젝트 경험"
    },
    {
      "questionOrder": 2,
      "questionText": "본인의 강점을 작성해주세요.",
      "toneOption": "formal",
      "lengthOption": "medium",
      "emphasisPoint": "협업"
    }
  ],
  "portfolioEvidence": [
    {
      "projectKey": "project_1",
      "projectName": "AI Interview Practice Platform",
      "summary": "포트폴리오 기반 취업 준비 플랫폼 개발 경험",
      "signals": ["Spring Boot", "OAuth2", "PostgreSQL"],
      "evidenceBullets": [
        "OAuth2 로그인 및 GitHub 연동 구현",
        "문서 업로드 및 텍스트 추출 처리"
      ],
      "confidence": "high"
    }
  ],
  "writingConstraints": {
    "forbidMadeUpMetrics": true,
    "language": "ko",
    "preferStarStructure": true
  },
  "existingEditedAnswers": []
}
```

### 6.2.4 System Prompt

```text
당신은 개발자 취업 준비 플랫폼의 자소서 문항 답변 생성 엔진이다.
주어진 자소서 문항, 직무 정보, 회사 정보, 포트폴리오 evidence를 바탕으로 문항별 답변 초안을 만든다.
없는 프로젝트, 없는 성과 수치, 없는 회사 정보는 만들지 말라.
가능하면 역할, 문제, 행동, 결과 흐름을 반영하라.
반드시 JSON object 하나만 출력하라.
```

### 6.2.5 Developer Prompt

```text
목표:
- 각 questionOrder마다 answerText를 생성한다.
- 문항과 직접 관련 있는 evidence만 선택해 사용한다.
- companyName이 없으면 범용 직무 맞춤 답변으로 생성한다.
- editedAnswer가 이미 존재하는 문항은 참고만 하고 덮어쓰지 않는다.

금지:
- 문항과 무관한 장황한 서론
- 숫자, 성과, 조직 규모, 사용자 수의 임의 생성
- 포트폴리오에 없는 기술 선택 배경 서사
- 동일 표현 반복

품질 규칙:
- toneOption과 lengthOption을 지킨다.
- emphasisPoint가 있으면 해당 포인트를 우선 반영한다.
- evidence 연결이 약하면 qualityFlags에 weak_evidence를 넣는다.
```

### 6.2.6 출력 스키마

```json
{
  "answers": [
    {
      "questionOrder": 1,
      "questionText": "지원 동기를 작성해주세요.",
      "answerText": "저는 포트폴리오 기반으로 자소서와 면접 준비를 연결하는 서비스를 직접 설계하고 구현하며...",
      "usedEvidenceKeys": ["project_1"],
      "qualityFlags": []
    }
  ],
  "qualityFlags": []
}
```

### 6.2.7 후처리 검증

- `questionOrder` 누락 금지
- 입력 문항 수와 출력 문항 수 일치 여부 확인
- `answerText` 최소 길이 검증
- `usedEvidenceKeys`는 `portfolioEvidence.projectKey`에 존재해야 함
- `qualityFlags` enum 검증

### 6.2.8 저장 매핑

- `application_questions.generated_answer`
- `application_questions.updated_at`

### 6.2.9 재시도 규칙

- malformed JSON: 동일 payload 1회 재시도
- schema violation: 동일 모델 1회 재시도
- 여전히 실패 시 `portfolioEvidence` 축약 후 1회 재시도
- 문항 일부만 성공하면 부분 성공 저장 허용

## 6.3 ai.interview.questions.generate.v1

### 6.3.1 목적

- 자소서 문항과 포트폴리오 evidence를 바탕으로 예상 면접 질문 세트를 생성한다.
- 경험 기반, 기술 기반, CS 기반, 꼬리질문 seed를 균형 있게 섞는다.

### 6.3.2 입력 변수

- `jobRole`
- `companyName`
- `applicationQuestions`
- `preferredQuestionCount`
- `difficultyLevel`
- `questionTypes`
- `portfolioEvidence`
- `questionGenerationRules`

### 6.3.3 입력 예시

```json
{
  "jobRole": "Backend Developer",
  "companyName": "OpenAI Korea",
  "applicationQuestions": [
    {
      "questionOrder": 1,
      "questionText": "지원 동기를 작성해주세요.",
      "finalAnswerText": "저는 포트폴리오 기반..."
    }
  ],
  "preferredQuestionCount": 10,
  "difficultyLevel": "medium",
  "questionTypes": ["experience", "technical_cs", "technical_stack", "follow_up"],
  "portfolioEvidence": [
    {
      "projectKey": "project_1",
      "projectName": "AI Interview Practice Platform",
      "signals": ["Spring Boot", "OAuth2", "PostgreSQL"],
      "evidenceBullets": ["OAuth2 로그인 및 GitHub 연동 구현"]
    }
  ],
  "questionGenerationRules": {
    "avoidQuestionCopy": true,
    "maxDuplicateSimilarity": 0.8,
    "includeCsBasicsWhenContextLow": true
  }
}
```

### 6.3.4 System Prompt

```text
당신은 개발자 기술 면접 예상 질문 생성 엔진이다.
자소서와 포트폴리오 evidence를 바탕으로 실제 면접에서 나올 법한 질문 세트를 구조화해서 생성하라.
질문은 자소서 문장을 그대로 복사하지 말고 면접 문맥으로 재구성하라.
중복 질문을 줄이고, 직무 관련성과 난이도 균형을 지켜라.
반드시 JSON object 하나만 출력하라.
```

### 6.3.5 Developer Prompt

```text
목표:
- preferredQuestionCount에 맞춰 질문을 생성한다.
- 질문 유형 분포를 가능한 한 맞춘다.
- 포트폴리오 근거가 부족하면 일반 직무/CS 질문 비중을 높인다.

질문 규칙:
- experience: 경험, 문제 해결, 협업, 의사결정 질문
- technical_stack: 기술 선택 이유, trade-off, 구현 디테일
- technical_cs: 직무 관련 기본 CS 질문
- follow_up: 특정 답변을 더 파고들 수 있는 seed 질문

금지:
- 자소서 문항 복사형 질문
- 거의 같은 의미의 질문 중복
- 직무와 무관한 뜬금없는 질문
- enum에 없는 questionType, difficultyLevel 사용
```

### 6.3.6 출력 스키마

```json
{
  "questions": [
    {
      "questionOrder": 1,
      "questionType": "experience",
      "difficultyLevel": "medium",
      "questionText": "가장 어려웠던 프로젝트 문제 해결 경험을 설명해주세요.",
      "sourceApplicationQuestionOrder": 1,
      "parentQuestionOrder": null,
      "usedEvidenceKeys": ["project_1"]
    }
  ],
  "qualityFlags": []
}
```

### 6.3.7 후처리 검증

- `questionOrder` 연속성 검증
- `questionType`, `difficultyLevel` enum 검증
- 동일/유사 질문 중복 제거
- `questionText` 최소 길이 검증
- 출력 개수가 `preferredQuestionCount`와 크게 어긋나면 재시도

### 6.3.8 저장 매핑

- `interview_question_sets`
- `interview_questions`
- `interview_questions.source_application_question_id`
- `interview_questions.parent_question_id`

### 6.3.9 재시도 규칙

- schema violation: 1회 재시도
- 질문 수 미달 또는 중복 과다: 질문 규칙 강화 후 1회 재시도
- 전체 구조 중요 기능이므로 부분 성공 저장 기본 비권장

## 6.4 ai.interview.followup.generate.v1

### 6.4.1 목적

- 현재 질문과 직전 답변을 바탕으로 꼬리 질문 1개를 생성한다.

### 6.4.2 입력 변수

- `jobRole`
- `companyName`
- `currentQuestion`
- `currentAnswer`
- `previousConversationSummary`
- `followUpDepth`
- `maxDepth`
- `questionSetContext`

### 6.4.3 입력 예시

```json
{
  "jobRole": "Backend Developer",
  "companyName": "OpenAI Korea",
  "currentQuestion": {
    "questionOrder": 3,
    "questionType": "technical_stack",
    "questionText": "왜 Spring Security를 선택했나요?"
  },
  "currentAnswer": {
    "answerText": "OAuth2와 권한 구조를 함께 다루기 쉬웠기 때문입니다.",
    "isSkipped": false
  },
  "previousConversationSummary": [
    "사용자는 인증/인가 설계를 강조했다.",
    "트레이드오프 설명은 아직 부족했다."
  ],
  "followUpDepth": 0,
  "maxDepth": 1,
  "questionSetContext": {
    "difficultyLevel": "medium"
  }
}
```

### 6.4.4 System Prompt

```text
당신은 텍스트 기반 모의 면접의 꼬리 질문 생성 엔진이다.
직전 질문과 직전 답변을 바탕으로 더 깊이 있는 후속 질문 1개만 생성하라.
답변에서 실제로 언급된 포인트를 파고들어야 하며, 전혀 새로운 주제로 이탈하지 말라.
반드시 JSON object 하나만 출력하라.
```

### 6.4.5 Developer Prompt

```text
조건:
- currentAnswer.isSkipped가 true면 follow-up을 만들지 않는다.
- 답변 길이가 너무 짧거나 실질 정보가 없으면 null 결과를 반환할 수 있다.
- maxDepth를 넘기지 않는다.

좋은 follow-up 기준:
- 선택 이유의 trade-off를 묻는다.
- 구현 세부, 성능 영향, 대안 비교를 묻는다.
- 경험 질문이면 결과 근거와 의사결정 기준을 묻는다.

금지:
- 예/아니오형 단답 질문
- 기존 질문을 거의 그대로 반복
- 전혀 새로운 기술 주제로 점프
```

### 6.4.6 출력 스키마

```json
{
  "followUpQuestion": {
    "questionType": "follow_up",
    "difficultyLevel": "medium",
    "questionText": "그 선택을 했을 때 다른 대안과 비교한 기준을 조금 더 구체적으로 설명해주실 수 있나요?",
    "parentQuestionOrder": 3
  },
  "qualityFlags": []
}
```

또는 생성하지 않는 경우

```json
{
  "followUpQuestion": null,
  "qualityFlags": ["low_context"]
}
```

### 6.4.7 후처리 검증

- `followUpQuestion`이 null이면 저장 생략 가능
- `questionType`은 반드시 `follow_up`
- `parentQuestionOrder`는 현재 질문 번호와 일치해야 함
- `followUpDepth` 제한 재검증

### 6.4.8 저장 매핑

- 세션 전용 임시 질문 또는 `interview_questions` 신규 row
- 최종 저장 방식은 후속 결정 항목으로 둔다.

### 6.4.9 재시도 규칙

- malformed JSON 1회 재시도
- 품질 미달 시 재시도보다 null 반환을 우선
- 실시간 UX 고려해 최대 1회 재시도

## 6.5 ai.interview.evaluate.v1

### 6.5.1 목적

- 종료된 세션의 질문별 답변을 평가해 질문별 점수, 근거, 태그를 생성한다.

### 6.5.2 입력 변수

- `jobRole`
- `companyName`
- `questionAnswerPairs`
- `tagMaster`
- `scoringRubric`
- `evaluationConstraints`
- `portfolioEvidence` 선택

### 6.5.3 입력 예시

```json
{
  "jobRole": "Backend Developer",
  "companyName": "OpenAI Korea",
  "questionAnswerPairs": [
    {
      "questionOrder": 1,
      "questionType": "experience",
      "questionText": "가장 어려웠던 프로젝트 문제 해결 경험을 설명해주세요.",
      "answerText": "저는 프로젝트에서 인증 구조를 설계하면서...",
      "isSkipped": false
    }
  ],
  "tagMaster": [
    {"tagName": "근거 부족", "tagCategory": "evidence"},
    {"tagName": "기술 깊이 부족", "tagCategory": "technical"},
    {"tagName": "답변 구조 미흡", "tagCategory": "structure"}
  ],
  "scoringRubric": {
    "relevance": 25,
    "structure": 20,
    "evidence": 20,
    "technicalAccuracy": 20,
    "clarity": 15
  },
  "evaluationConstraints": {
    "scoreMin": 0,
    "scoreMax": 100,
    "maxTagsPerAnswer": 3,
    "useOnlyGivenTags": true
  }
}
```

### 6.5.4 System Prompt

```text
당신은 개발자 기술 면접 답변 평가 엔진이다.
종료된 세션의 질문과 답변을 읽고, 질문별 점수와 짧은 평가 근거, 약점 태그를 JSON으로 생성하라.
점수는 엄격하지만 일관되게 부여하라.
질문 유형에 맞는 평가 기준을 적용하라.
공격적 표현, 성격 단정, 합격/불합격 단정은 금지한다.
반드시 JSON object 하나만 출력하라.
```

### 6.5.5 Developer Prompt

```text
평가 축:
- relevance
- structure
- evidence
- technicalAccuracy
- clarity

질문 유형별 보정:
- experience: 문제-행동-결과 연결성 강조
- technical_cs: 개념 정확성과 핵심어 강조
- technical_stack: 선택 이유와 trade-off 강조
- behavioral: 상황 설명과 협업 맥락 강조

규칙:
- isSkipped=true면 score는 매우 낮게 부여한다.
- rationale은 1~2문장으로 짧게 쓴다.
- 태그는 tagMaster 안에서만 선택한다.
- 태그 수는 최대 3개다.
- summary는 전체 답변 경향을 요약해야 한다.
```

### 6.5.6 출력 스키마

```json
{
  "totalScore": 82,
  "summaryFeedback": "기술 설명은 비교적 안정적이지만 일부 답변에서 근거와 트레이드오프 설명이 부족했습니다.",
  "answers": [
    {
      "questionOrder": 1,
      "score": 80,
      "evaluationRationale": "상황 설명은 명확했지만 결과를 보여주는 구체 근거가 부족합니다.",
      "tagNames": ["근거 부족"]
    }
  ],
  "qualityFlags": []
}
```

### 6.5.7 후처리 검증

- `totalScore`, `answers[].score` 범위 검증
- `questionOrder`가 입력 질문과 일치해야 함
- `tagNames`는 `tagMaster`에 존재해야 함
- 빈 `evaluationRationale` 금지
- `answers` 개수는 평가 대상 질문 수와 일치해야 함

### 6.5.8 저장 매핑

- `interview_sessions.total_score`
- `interview_sessions.summary_feedback`
- `interview_answers.score`
- `interview_answers.evaluation_rationale`
- `interview_answer_tags`

### 6.5.9 재시도 규칙

- malformed JSON 또는 schema violation 시 1회 재시도
- 점수 범위 오류 시 동일 모델 1회 재시도
- 전체 일관성이 중요하므로 부분 성공 저장 비권장

## 6.6 ai.interview.summary.v1

### 6.6.1 목적

- 질문별 평가 결과를 요약해 세션 전반의 강점/약점/다음 개선 포인트를 짧게 정리한다.
- 사용자 UI에서 결과 카드나 히스토리 미리보기에 사용한다.

### 6.6.2 입력 변수

- `jobRole`
- `companyName`
- `answerEvaluations`
- `topStrengthSignals`
- `topWeaknessSignals`
- `summaryConstraints`

### 6.6.3 System Prompt

```text
당신은 면접 결과 요약 생성 엔진이다.
질문별 평가 결과를 바탕으로 세션의 전체 경향을 짧고 명확하게 요약하라.
새로운 사실을 만들지 말고, 이미 평가된 내용만 집약하라.
반드시 JSON object 하나만 출력하라.
```

### 6.6.4 Developer Prompt

```text
목표:
- strengths 1~3개
- weaknesses 1~3개
- nextActions 1~3개
- shortSummary 2~3문장

금지:
- 질문별 세부 rationale를 그대로 장황하게 반복
- 새로운 태그나 새로운 평가 요소 생성
- 합격/불합격 예측
```

### 6.6.5 출력 스키마

```json
{
  "shortSummary": "기술 선택 이유를 설명하는 답변은 안정적이었지만, 일부 경험 질문에서 결과 근거가 약했습니다.",
  "strengths": ["기술 개념 설명이 비교적 명확함"],
  "weaknesses": ["구체 근거 제시 부족"],
  "nextActions": ["프로젝트 결과를 수치 또는 비교 기준으로 정리하기"],
  "qualityFlags": []
}
```

### 6.6.6 후처리 검증

- 각 배열 최대 길이 제한 검증
- 중복 항목 제거
- `shortSummary` 최소/최대 길이 검증

### 6.6.7 저장/노출 방식

- 별도 테이블 저장 없이 API 응답 합성 또는 캐시 사용 가능
- 필요 시 `interview_sessions.summary_feedback` 보조 생성에 활용

## 7. 공통 JSON Schema 처리 원칙

### 7.1 저장 전 검증 순서

1. JSON parse 성공 여부
2. 최상위 object 여부
3. 필수 필드 존재 여부
4. enum 검증
5. 길이 및 개수 검증
6. cross-field 검증
7. DB 저장 가능 여부 검증

### 7.2 cross-field 검증 예시

- 질문 생성 결과의 `questionOrder`는 1부터 연속이어야 한다.
- 평가 결과의 `answers[].questionOrder`는 실제 세션 질문에 모두 존재해야 한다.
- 자소서 생성 결과의 `usedEvidenceKeys`는 summary output의 key와 매핑되어야 한다.
- follow-up 결과의 `parentQuestionOrder`는 현재 질문 번호와 같아야 한다.

### 7.3 저장 금지 조건

- enum 미등록 값 발견
- 점수 범위 초과
- 빈 answerText, 빈 questionText
- 태그 마스터에 없는 tag 사용
- 입력 문항/질문 수와 출력 수 불일치가 큰 경우

## 8. 재시도 및 fallback 정책

### 8.1 재시도 우선순위

1. 동일 payload 재시도
2. schema 강조 문구를 강화한 developer prompt 재시도
3. 컨텍스트 축약 후 재시도
4. fallback model로 재시도
5. 최종 실패 반환

### 8.2 템플릿별 기본 재시도 횟수

- `ai.portfolio.summary.v1`: 최대 2회
- `ai.self_intro.generate.v1`: 최대 2회
- `ai.interview.questions.generate.v1`: 최대 2회
- `ai.interview.followup.generate.v1`: 최대 1회
- `ai.interview.evaluate.v1`: 최대 2회
- `ai.interview.summary.v1`: 최대 1회

### 8.3 실패 시 사용자 메시지 원칙

- 자소서 생성 중 오류가 발생했습니다. 다시 시도해주세요.
- 면접 질문 생성 중 오류가 발생했습니다. 다시 시도해주세요.
- 꼬리 질문 생성에 실패했습니다. 다음 질문으로 진행합니다.
- 면접 결과 생성 중 오류가 발생했습니다. 다시 시도해주세요.

## 9. 구현 가이드

### 9.1 권장 코드 구조

```text
ai/
  templates/
    system/
      common-system.txt
    developer/
      ai.portfolio.summary.v1.txt
      ai.self_intro.generate.v1.txt
      ai.interview.questions.generate.v1.txt
      ai.interview.followup.generate.v1.txt
      ai.interview.evaluate.v1.txt
      ai.interview.summary.v1.txt
  schema/
    portfolio-summary.schema.json
    self-intro-generate.schema.json
    interview-questions-generate.schema.json
    interview-followup-generate.schema.json
    interview-evaluate.schema.json
    interview-summary.schema.json
  registry/
    prompt-template-registry.json
```

### 9.2 레지스트리 예시

```json
{
  "templateId": "ai.interview.evaluate.v1",
  "version": "v1",
  "taskType": "interview_evaluation",
  "systemPromptFile": "system/common-system.txt",
  "developerPromptFile": "developer/ai.interview.evaluate.v1.txt",
  "schemaFile": "schema/interview-evaluate.schema.json",
  "temperature": 0.2,
  "maxTokens": 2500,
  "retryPolicy": {
    "maxRetries": 2,
    "allowFallback": true
  }
}
```

### 9.3 테스트 포인트

- prompt payload builder가 누락 필드 없이 값을 채우는지
- JSON schema validator가 enum/length를 잘 막는지
- retry/fallback 경로가 과도하게 반복 호출되지 않는지
- 평가 태그가 tagMaster 범위를 벗어나지 않는지
- follow-up 생성 시 null 반환 경로가 정상 동작하는지

## 10. 미결정 항목

- 자소서 생성 결과의 문항별 최대 글자 수
- `qualityFlags`를 DB에 저장할지 API 응답용으로만 둘지
- `ai.portfolio.summary.v1` 결과를 Redis 캐시에만 둘지 별도 테이블로 둘지
- 꼬리 질문을 `interview_questions`에 영구 저장할지 세션 메모리/Redis에 둘지
- 평가 결과에서 태그를 마스터 테이블 고정으로만 운영할지 일부 자유 태그를 허용할지
- 템플릿 파일을 코드 저장소에 둘지 관리자 콘솔에서 버전 관리할지

## 11. 추천 다음 단계

- 이 문서를 기준으로 템플릿별 `.txt` 파일과 `.schema.json` 파일 초안을 만든다.
- 다음으로는 `error-codes.md`와 `backend-conventions.md`에 AI 오류 처리 규칙을 반영한다.
- 구현 시작 전 고정 fixture 기반 AI 회귀 테스트 케이스를 먼저 만든다.

package com.back.backend.domain.github.analysis;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 커밋 subject를 보고 AI 파이프라인 포함 여부를 결정한다.
 */
@Component
public class CommitClassifier {

    /**
     * IGNORED 판정 패턴 — 토큰 예산을 갉아먹는 모든 노이즈를 원천 차단한다.
     */
    private static final Pattern IGNORED_SUBJECT_PATTERN =
        Pattern.compile(
            // 1. 범용 / 컨벤션 (스타일, 포맷팅, 빌드, 리팩토링 등)
            "^(docs|chore|style|test|build|cleanup|clean|format|lint|typo|wip|draft|bump|deps|minor|trivial)[:(\\[]|" +

                // 2. 다국어(i18n) 및 디자인 정적 자원 (로직 없음)
                "^(i18n|l10n|translation|asset|icon|font)[:(\\[\\s]|" +

                // 3. 프론트엔드/백엔드 테스트 부수 파일 및 더미 데이터
                "^(mock|stub|fixture|snapshot|dummy)[:(\\[\\s]|" +

                // 4. 배포, 버전 펌핑, 패키징 태그
                "^(release|version|publish|tag|cut\\s+release|v\\d+\\.\\d+)[:(\\[\\s]|" +

                // 5. 자동화 봇 및 CI 병합 메시지
                "^(renovate|snyk|dependabot|all-contributors|merge\\s+pull\\s+request|merge\\s+branch|initial\\s+commit)|" +

                // 6. 개발자의 "아무 말" (급한 수정, 실수, 임시 저장)
                "^(oops|whoops|asdf|test\\s*commit|temp\\s*commit)|" +

                // 7. 한국어 관용구 (대괄호나 띄어쓰기로 시작하는 비핵심 작업들)
                "^\\[?(문서|주석|오타|띄어쓰기|정리|단순|테스트|빌드|의존성|설정|임시|자잘한|번역|버전|배포)\\]?\\s+",

            Pattern.CASE_INSENSITIVE
        );

    /**
     * @param subject 커밋 메시지 첫 줄
     * @return {@link CommitCategory#IGNORED} 또는 {@link CommitCategory#INCLUDED}
     */
    public CommitCategory classify(String subject) {
        if (IGNORED_SUBJECT_PATTERN.matcher(subject).find()) {
            return CommitCategory.IGNORED;
        }
        return CommitCategory.INCLUDED;
    }
}

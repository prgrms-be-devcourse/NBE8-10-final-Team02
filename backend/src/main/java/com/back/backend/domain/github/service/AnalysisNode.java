package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.NodeType;

import java.util.List;

/**
 * 정적 분석기가 추출한 코드 노드 (클래스 or 함수).
 *
 * StaticAnalysisService → CallGraphService → CodeIndexService 흐름에서 사용된다.
 * DB 저장 전 중간 표현이므로 외부에 노출하지 않는다.
 */
public record AnalysisNode(
        String fqn,               // 완전한 식별자 (예: com.example.auth.JwtAuthFilter)
        String filePath,           // repo 루트 기준 상대 경로
        int locStart,
        int locEnd,
        NodeType nodeType,
        List<String> calls,       // 이 노드가 참조하는 FQN 목록 (Call Graph 엣지)
        List<MethodInfo> methods  // CLASS 타입일 때 포함된 메서드 목록
) {
    public record MethodInfo(
            String name,
            String signature,     // 예: doFilter(ServletRequest,ServletResponse,FilterChain):void
            int locStart,
            int locEnd
    ) {}
}

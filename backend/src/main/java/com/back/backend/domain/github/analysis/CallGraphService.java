package com.back.backend.domain.github.analysis;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Call Graph 기반 PageRank 계산 서비스.
 *
 * 그래프 방향:
 *   A가 B를 호출/참조 → A→B 엣지 → B의 in-degree 증가 → B의 PageRank 상승
 *   (많이 호출되는 핵심 클래스가 높은 PageRank를 가짐)
 *
 * 결과 활용:
 *   - Token Budget 적용 시 PageRank 높은 순으로 LLM 컨텍스트 포함
 *   - 임계값(0.6→0.7→0.8)을 높여 토큰 예산 초과 시 점진적으로 제외
 */
@Service
public class CallGraphService {

    private static final int ITERATIONS = 25;
    private static final double DAMPING = 0.85;
    private static final double INITIAL_RANK = 1.0;

    /**
     * AnalysisNode 목록에서 PageRank를 계산한다.
     *
     * @param nodes 분석된 노드 목록 (calls = 이 노드가 참조하는 FQN)
     * @return fqn → pagerank 맵 (값 범위 0.0~1.0 정규화)
     */
    public Map<String, Double> computePageRank(List<AnalysisNode> nodes) {
        if (nodes.isEmpty()) return Map.of();

        // FQN 집합 (그래프에 실제 존재하는 노드만)
        Set<String> allFqns = new HashSet<>();
        nodes.forEach(n -> allFqns.add(n.fqn()));

        // 역방향 인접 리스트: fqn → 이 fqn을 호출하는 노드들
        Map<String, Set<String>> inEdges = new HashMap<>();
        // 순방향 out-degree
        Map<String, Integer> outDegree = new HashMap<>();

        allFqns.forEach(fqn -> {
            inEdges.put(fqn, new HashSet<>());
            outDegree.put(fqn, 0);
        });

        for (AnalysisNode node : nodes) {
            int callCount = 0;
            for (String callee : node.calls()) {
                if (allFqns.contains(callee) && !callee.equals(node.fqn())) {
                    inEdges.get(callee).add(node.fqn());
                    callCount++;
                }
            }
            outDegree.put(node.fqn(), callCount);
        }

        // PageRank 초기화
        int n = allFqns.size();
        Map<String, Double> rank = new HashMap<>();
        double initialRank = INITIAL_RANK / n;
        for (String fqn : allFqns) {
            rank.put(fqn, initialRank);
        }

        // 반복 계산
        for (int iter = 0; iter < ITERATIONS; iter++) {
            Map<String, Double> newRank = new HashMap<>();

            for (String fqn : allFqns) {
                double sum = 0.0;
                for (String caller : inEdges.get(fqn)) {
                    int od = outDegree.getOrDefault(caller, 1);
                    sum += rank.get(caller) / Math.max(od, 1);
                }
                newRank.put(fqn, (1.0 - DAMPING) / n + DAMPING * sum);
            }

            rank = newRank;
        }

        return normalize(rank);
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    /**
     * PageRank 값을 0.0~1.0 범위로 정규화한다.
     * min-max normalization 사용.
     */
    private Map<String, Double> normalize(Map<String, Double> rank) {
        if (rank.isEmpty()) return rank;

        double min = rank.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = rank.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double range = max - min;

        if (range == 0.0) {
            // 모든 노드 동일 rank → 균등 분배
            Map<String, Double> uniform = new HashMap<>();
            rank.keySet().forEach(k -> uniform.put(k, 0.5));
            return uniform;
        }

        Map<String, Double> normalized = new HashMap<>();
        rank.forEach((fqn, r) -> normalized.put(fqn, (r - min) / range));
        return normalized;
    }
}

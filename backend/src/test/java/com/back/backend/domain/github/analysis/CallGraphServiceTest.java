package com.back.backend.domain.github.analysis;

import com.back.backend.domain.github.entity.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallGraphServiceTest {

    private final CallGraphService service = new CallGraphService();

    private static AnalysisNode node(String fqn, List<String> calls) {
        return new AnalysisNode(fqn, "src/" + fqn + ".java", 1, 10, NodeType.CLASS, calls, List.of());
    }

    // ─────────────────────────────────────────────────
    // 기본 케이스
    // ─────────────────────────────────────────────────

    @Test
    void emptyInput_returnsEmptyMap() {
        Map<String, Double> result = service.computePageRank(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void singleNode_noEdges_returnsNormalizedScore() {
        // 노드가 하나이고 out-edge가 없으면 모든 노드가 동일 rank → normalize → 0.5
        Map<String, Double> result = service.computePageRank(
                List.of(node("A", List.of())));

        assertThat(result).containsKey("A");
        assertThat(result.get("A")).isEqualTo(0.5);
    }

    @Test
    void allResults_areBetween0And1() {
        // A → B, B → C, A → C (C가 2개에서 참조 → 높은 PageRank 예상)
        List<AnalysisNode> nodes = List.of(
                node("A", List.of("B", "C")),
                node("B", List.of("C")),
                node("C", List.of())
        );

        Map<String, Double> result = service.computePageRank(nodes);

        result.values().forEach(rank ->
                assertThat(rank).isBetween(0.0, 1.0));
    }

    // ─────────────────────────────────────────────────
    // 허브 노드
    // ─────────────────────────────────────────────────

    @Test
    void hubNode_receivingMostEdges_getsHighestRank() {
        // Service(A, B, C) → Core(D): D가 모든 노드에서 참조 → 최고 PageRank
        List<AnalysisNode> nodes = List.of(
                node("ServiceA", List.of("Core")),
                node("ServiceB", List.of("Core")),
                node("ServiceC", List.of("Core")),
                node("Core", List.of())
        );

        Map<String, Double> result = service.computePageRank(nodes);

        assertThat(result.get("Core")).isGreaterThan(result.get("ServiceA"));
        assertThat(result.get("Core")).isGreaterThan(result.get("ServiceB"));
        assertThat(result.get("Core")).isGreaterThan(result.get("ServiceC"));
    }

    @Test
    void isolatedNode_getsLowerRankThanHub() {
        List<AnalysisNode> nodes = List.of(
                node("Caller1", List.of("Hub")),
                node("Caller2", List.of("Hub")),
                node("Hub", List.of()),
                node("Isolated", List.of())
        );

        Map<String, Double> result = service.computePageRank(nodes);

        assertThat(result.get("Hub")).isGreaterThan(result.get("Isolated"));
    }

    // ─────────────────────────────────────────────────
    // 셀프 루프 / 외부 참조
    // ─────────────────────────────────────────────────

    @Test
    void selfCallsAreIgnored_doesNotInflateOwnRank() {
        // A가 자기 자신을 참조해도 A의 in-degree는 늘어나지 않아야 함
        List<AnalysisNode> nodes = List.of(
                node("A", List.of("A", "B")),
                node("B", List.of())
        );

        Map<String, Double> result = service.computePageRank(nodes);

        // B는 A에서만 참조, A는 self-call(제외) → B가 A보다 높거나 같아야 함
        assertThat(result.get("B")).isGreaterThanOrEqualTo(result.get("A"));
    }

    @Test
    void externalCallsOutsideGraph_areIgnored() {
        // "external.Library"는 그래프에 없으므로 edge가 형성되지 않아야 함
        List<AnalysisNode> nodes = List.of(
                node("A", List.of("external.Library", "B")),
                node("B", List.of())
        );

        // 결과 맵에 external.Library가 없어야 함
        Map<String, Double> result = service.computePageRank(nodes);

        assertThat(result).doesNotContainKey("external.Library");
        assertThat(result).containsKeys("A", "B");
    }

    // ─────────────────────────────────────────────────
    // 정규화
    // ─────────────────────────────────────────────────

    @Test
    void noEdgesInGraph_allNodesGetUniformRank() {
        // 모든 노드가 고립 → 동일 rank → normalize 시 모두 0.5
        List<AnalysisNode> nodes = List.of(
                node("A", List.of()),
                node("B", List.of()),
                node("C", List.of())
        );

        Map<String, Double> result = service.computePageRank(nodes);

        assertThat(result.values()).allMatch(r -> r == 0.5);
    }

    @Test
    void topRankedNode_hasScore1_bottomHasScore0() {
        // 엣지가 있는 경우 max=1.0, min=0.0 정규화 확인
        List<AnalysisNode> nodes = List.of(
                node("Caller", List.of("Target")),
                node("Target", List.of())
        );

        Map<String, Double> result = service.computePageRank(nodes);

        double max = result.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double min = result.values().stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        assertThat(max).isEqualTo(1.0);
        assertThat(min).isEqualTo(0.0);
    }
}

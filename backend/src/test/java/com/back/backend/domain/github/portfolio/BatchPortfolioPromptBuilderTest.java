package com.back.backend.domain.github.portfolio;

import com.back.backend.domain.github.analysis.DiffEntry;
import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchPortfolioPromptBuilderTest {

    private BatchPortfolioPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new BatchPortfolioPromptBuilder();
    }

    // ─────────────────────────────────────────────────────────────────────
    // XML 구조 검증
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("XML 구조")
    class XmlStructure {

        @Test
        @DisplayName("최상위 <batch_data> 태그로 감싸진다")
        void build_wrapsWithBatchDataTag() {
            List<BatchPortfolioPromptBuilder.RepoBatchData> data = List.of(makeRepoBatchData("my-repo"));
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(data, budget);

            assertThat(xml).startsWith("<batch_data>").endsWith("</batch_data>");
        }

        @Test
        @DisplayName("각 repo는 <repository id=repoKey> 태그로 감싸진다")
        void build_wrapsEachRepoWithRepositoryTag() {
            List<BatchPortfolioPromptBuilder.RepoBatchData> data = List.of(
                    makeRepoBatchData("my-project"),
                    makeRepoBatchData("other-repo")
            );
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 2);

            String xml = builder.build(data, budget);

            assertThat(xml).contains("<repository id=\"my-project\"");
            assertThat(xml).contains("<repository id=\"other-repo\"");
            assertThat(xml).contains("</repository>");
        }

        @Test
        @DisplayName("repoName의 특수문자는 소문자 하이픈으로 변환된다")
        void build_convertsRepoNameToKebabCase() {
            List<BatchPortfolioPromptBuilder.RepoBatchData> data = List.of(
                    makeRepoBatchData("My_Project.v2"));
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(data, budget);

            assertThat(xml).contains("id=\"my-project-v2\"");
        }

        @Test
        @DisplayName("overview가 있으면 <overview> 태그가 포함된다")
        void build_includesOverviewWhenPresent() {
            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data = new BatchPortfolioPromptBuilder.RepoBatchData(
                    repo, true, "This is the README content", List.of(), List.of(), List.of(), List.of());
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).contains("<overview>").contains("This is the README content");
        }

        @Test
        @DisplayName("overview가 null이면 <overview> 태그가 없다")
        void build_omitsOverviewWhenNull() {
            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data = new BatchPortfolioPromptBuilder.RepoBatchData(
                    repo, true, null, List.of(), List.of(), List.of(), List.of());
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).doesNotContain("<overview>");
        }

        @Test
        @DisplayName("overview가 blank이면 <overview> 태그가 없다")
        void build_omitsOverviewWhenBlank() {
            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data = new BatchPortfolioPromptBuilder.RepoBatchData(
                    repo, true, "   ", List.of(), List.of(), List.of(), List.of());
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).doesNotContain("<overview>");
        }

        @Test
        @DisplayName("<code_structure> 태그가 항상 포함된다")
        void build_alwaysIncludesCodeStructureTag() {
            List<BatchPortfolioPromptBuilder.RepoBatchData> data = List.of(makeRepoBatchData("my-repo"));
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data.get(0)), budget);

            assertThat(xml).contains("<code_structure>").contains("</code_structure>");
        }

        @Test
        @DisplayName("<diffs> 태그가 항상 포함된다")
        void build_alwaysIncludesDiffsTag() {
            List<BatchPortfolioPromptBuilder.RepoBatchData> data = List.of(makeRepoBatchData("my-repo"));
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data.get(0)), budget);

            assertThat(xml).contains("<diffs>").contains("</diffs>");
        }

        @Test
        @DisplayName("repoName의 XML 특수문자가 이스케이프된다")
        void build_escapesXmlSpecialCharsInRepoName() {
            GithubRepository repo = mockRepo("repo");
            when(repo.getRepoName()).thenReturn("repo");
            // repo name으로 사용하는 name 속성에 따옴표가 들어갈 수 있는 경우를 검증
            // (id는 소문자+하이픈으로만 구성되어 이스케이프 불필요)
            // name 속성은 원본 repoName을 사용하므로 escapeXml이 적용됨
            GithubRepository repoWithSpecialName = mock(GithubRepository.class);
            when(repoWithSpecialName.getRepoName()).thenReturn("repo & test");

            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repoWithSpecialName, true, null, List.of(), List.of(), List.of(), List.of());
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).contains("name=\"repo &amp; test\"");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // code_structure 섹션
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("code_structure 섹션")
    class CodeStructureSection {

        @Test
        @DisplayName("authored 파일이 앞에, pagerank 내림차순으로 정렬된다")
        void build_sortsAuthoredFirstThenByPagerank() {
            CodeIndex authored = mockCodeIndex("com.example.AuthService", true, 0.3);
            CodeIndex highRank = mockCodeIndex("com.example.PayService", false, 0.9);
            CodeIndex lowRank  = mockCodeIndex("com.example.LogService", false, 0.2);

            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repo, true, null,
                            List.of(highRank, authored, lowRank), // 순서 섞어서
                            List.of(), List.of(), List.of());
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            int authoredPos  = xml.indexOf("AuthService");
            int highRankPos  = xml.indexOf("PayService");
            int lowRankPos   = xml.indexOf("LogService");

            // 핵심 검증: lowRank는 필터링되어 아예 존재하지 않아야 함 (-1)
            assertThat(lowRankPos).isEqualTo(-1);

            // 살아남은 것들끼리의 정렬 순서 검증
            assertThat(authoredPos).isNotEqualTo(-1);
            assertThat(highRankPos).isNotEqualTo(-1);
            assertThat(authoredPos).isLessThan(highRankPos);
        }

        @Test
        @DisplayName("authored 파일에는 '[authored]' 마커가 붙는다")
        void build_marksAuthoredEntries() {
            CodeIndex authored = mockCodeIndex("com.example.MyService", true, null);

            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repo, true, null, List.of(authored),
                            List.of(), List.of(), List.of());
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).contains("[authored] com.example.MyService");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // diff 3단계 Fallback
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("diff 3단계 Fallback")
    class DiffFallback {

        @Test
        @DisplayName("예산 내에 들어오면 전체 diff가 포함된다")
        void buildDiffPhase_includesFullEntryWhenWithinBudget() {
            DiffEntry entry = new DiffEntry("abc1234", "feat: add auth", "", "small diff");
            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repo, true, null, List.of(),
                            List.of(entry), List.of(), List.of()); // early phase
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).contains("small diff");
            assertThat(xml).doesNotContain("[diff omitted");
        }

        @Test
        @DisplayName("diff가 예산을 초과하면 '[diff omitted: budget]' 요약본으로 대체된다")
        void buildDiffPhase_usesSummaryWhenDiffExceedsBudget() {
            // 매우 큰 diff로 예산 초과 유도
            String hugeDiff = "x".repeat(600_000);
            DiffEntry entry = new DiffEntry("abc1234", "feat: huge change", "body text", hugeDiff);

            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repo, true, null, List.of(),
                            List.of(entry), List.of(), List.of());
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).contains("[diff omitted: budget]");
            assertThat(xml).contains("feat: huge change");
            assertThat(xml).contains("body text");
            assertThat(xml).doesNotContain(hugeDiff);
        }

        @Test
        @DisplayName("예산 초과 커밋 이후에도 짧은 커밋은 포함된다 (break 아닌 continue)")
        void buildDiffPhase_continuesAfterBudgetExceeded() {
            // 첫 번째 커밋: diff가 매우 크고 subject도 길어서 3단계 모두 초과
            String hugeDiff = "x".repeat(600_000);
            String longSubject = "s".repeat(600_000);
            DiffEntry bigEntry = new DiffEntry("aaa1111", longSubject, "", hugeDiff);

            // 두 번째 커밋: 매우 짧음
            DiffEntry smallEntry = new DiffEntry("bbb2222", "fix: typo", "", "tiny");

            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repo, true, null, List.of(),
                            List.of(bigEntry, smallEntry), List.of(), List.of());
            // 작은 예산을 주어 bigEntry 전체/요약본을 모두 초과하게 함
            BatchTokenBudget budget = new BatchTokenBudget(100, 1);

            String xml = builder.build(List.of(data), budget);

            // smallEntry의 subject는 들어가야 함
            assertThat(xml).contains("fix: typo");
        }

        @Test
        @DisplayName("diff가 없는 구간은 <diff phase=...> 태그 자체가 없다")
        void buildDiffPhase_emitsNothingForEmptyPhase() {
            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repo, true, null, List.of(),
                            List.of(), List.of(), List.of()); // 모두 빈 리스트
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).doesNotContain("<diff phase=");
        }

        @Test
        @DisplayName("diff가 있으면 <diff phase='early|mid|late'> 태그가 포함된다")
        void buildDiffPhase_emitsCorrectPhaseTag() {
            DiffEntry early = new DiffEntry("aaa", "early commit", "", "diff-early");
            DiffEntry mid   = new DiffEntry("bbb", "mid commit", "", "diff-mid");
            DiffEntry late  = new DiffEntry("ccc", "late commit", "", "diff-late");

            GithubRepository repo = mockRepo("my-repo");
            BatchPortfolioPromptBuilder.RepoBatchData data =
                    new BatchPortfolioPromptBuilder.RepoBatchData(
                            repo, true, null, List.of(),
                            List.of(early), List.of(mid), List.of(late));
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            String xml = builder.build(List.of(data), budget);

            assertThat(xml).contains("<diff phase=\"early\">");
            assertThat(xml).contains("<diff phase=\"mid\">");
            assertThat(xml).contains("<diff phase=\"late\">");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    private BatchPortfolioPromptBuilder.RepoBatchData makeRepoBatchData(String repoName) {
        return new BatchPortfolioPromptBuilder.RepoBatchData(
                mockRepo(repoName), true, null, List.of(), List.of(), List.of(), List.of());
    }

    private GithubRepository mockRepo(String repoName) {
        GithubRepository repo = mock(GithubRepository.class);
        when(repo.getRepoName()).thenReturn(repoName);
        return repo;
    }

    private CodeIndex mockCodeIndex(String fqn, boolean authoredByMe, Double pagerank) {
        CodeIndex ci = mock(CodeIndex.class);
        when(ci.getFqn()).thenReturn(fqn);
        when(ci.isAuthoredByMe()).thenReturn(authoredByMe);
        when(ci.getPagerank()).thenReturn(pagerank);
        return ci;
    }
}

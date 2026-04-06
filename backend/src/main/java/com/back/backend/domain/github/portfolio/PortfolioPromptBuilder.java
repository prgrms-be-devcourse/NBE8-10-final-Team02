package com.back.backend.domain.github.portfolio;

import com.back.backend.domain.github.analysis.DiffEntry;
import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RepoSummary 생성 요청용 AI userMessage 빌더.
 *
 * <h3>userMessage 구성</h3>
 * <ol>
 *   <li>## Repository — repo 기본 정보</li>
 *   <li>## Project Overview (README) — 프로젝트 개요 (nullable)</li>
 *   <li>## Code Structure — PageRank 필터링된 클래스 목록</li>
 *   <li>## Contribution Commits — IGNORED 제거된 본인 커밋 (diff + body 포함)</li>
 * </ol>
 *
 * evidenceBullets / challenges / techDecisions 분류는 AI에 위임한다.
 * body와 diff를 함께 제공하므로 AI가 WHY:/NOTE: 주석 기반 techDecisions도 스스로 추출할 수 있다.
 *
 * <h3>Token Budget</h3>
 * 본인 소유 repo: 코드 구조 30K + diff 50K = 80K context<br>
 * 외부(org/오픈소스) repo: 코드 구조 10K + diff 40K = 50K context
 */
@Component
public class PortfolioPromptBuilder {

    // 토큰 예산 (문자 수 기준, 1 token ≈ 4 chars)
    private static final int OWNED_REPO_CODE_BUDGET    = 30_000 * 4;
    private static final int OWNED_REPO_DIFF_BUDGET    = 50_000 * 4;
    private static final int EXTERNAL_REPO_CODE_BUDGET = 10_000 * 4;
    private static final int EXTERNAL_REPO_DIFF_BUDGET = 40_000 * 4;

    // PageRank 임계값 단계 (예산 초과 시 단계적으로 높임)
    private static final double[] PAGERANK_THRESHOLDS = {0.6, 0.7, 0.8};

    /**
     * AI userMessage를 구성한다.
     *
     * @param repo            대상 repo
     * @param authorEmail     본인 GitHub primary email
     * @param codeEntries     CodeIndex 전체 (pagerank + authoredByMe 포함)
     * @param diffs           IGNORED 제거된 본인 커밋 diff 목록
     * @param isOwnedRepo     본인 소유 repo 여부 (token budget 차등 적용)
     * @param projectOverview README 등 프로젝트 개요 텍스트 (nullable)
     */
    public String buildUserMessage(
            GithubRepository repo,
            String authorEmail,
            List<CodeIndex> codeEntries,
            List<DiffEntry> diffs,
            boolean isOwnedRepo,
            String projectOverview
    ) {
        int codeBudget = isOwnedRepo ? OWNED_REPO_CODE_BUDGET : EXTERNAL_REPO_CODE_BUDGET;
        int diffBudget = isOwnedRepo ? OWNED_REPO_DIFF_BUDGET : EXTERNAL_REPO_DIFF_BUDGET;

        StringBuilder sb = new StringBuilder();

        // --- Repo 기본 정보 ---
        sb.append("## Repository\n");
        sb.append("name: ").append(repo.getFullName()).append("\n");
        sb.append("url: ").append(repo.getHtmlUrl()).append("\n");
        if (repo.getRepoSizeKb() != null) {
            sb.append("size_kb: ").append(repo.getRepoSizeKb()).append("\n");
        }
        sb.append("author_email: ").append(authorEmail).append("\n\n");

        // --- 프로젝트 개요 (README) ---
        if (projectOverview != null && !projectOverview.isBlank()) {
            sb.append("## Project Overview (README)\n");
            sb.append(projectOverview).append("\n\n");
        }

        // --- 코드 구조 (PageRank 필터링) ---
        sb.append("## Code Structure (authored classes highlighted)\n");
        sb.append(buildCodeSection(codeEntries, codeBudget)).append("\n\n");

        // --- 기여 커밋 ---
        // docs/chore/style 등 IGNORED 커밋은 사전에 제거됨.
        // evidenceBullets / challenges / techDecisions 분류는 AI가 수행한다.
        sb.append("## Contribution Commits\n");
        sb.append(buildDiffSection(diffs, diffBudget));

        return sb.toString();
    }

    // ─────────────────────────────────────────────────
    // 내부 빌더
    // ─────────────────────────────────────────────────

    private String buildDiffSection(List<DiffEntry> diffs, int budget) {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (DiffEntry d : diffs) {
            StringBuilder entry = new StringBuilder();
            entry.append("### ").append(d.sha()).append(" ").append(d.subject()).append("\n");
            if (!d.body().isBlank()) {
                entry.append("body:\n").append(d.body()).append("\n");
            }
            entry.append(d.diff()).append("\n");

            if (used + entry.length() > budget) {
                sb.append("... [remaining diffs truncated due to token budget]\n");
                break;
            }
            sb.append(entry);
            used += entry.length();
        }
        return sb.toString();
    }

    /**
     * CodeIndex 목록을 토큰 예산 내에서 텍스트로 직렬화한다.
     * authored_by_me=true 항목을 먼저 포함하고, 이후 pagerank 내림차순.
     * 예산 초과 시 PageRank 임계값을 높여 항목 수를 줄인다.
     */
    private String buildCodeSection(List<CodeIndex> entries, int budget) {
        List<CodeIndex> sorted = entries.stream()
                .sorted((a, b) -> {
                    if (a.isAuthoredByMe() != b.isAuthoredByMe()) {
                        return a.isAuthoredByMe() ? -1 : 1;
                    }
                    double pa = a.getPagerank() != null ? a.getPagerank() : 0.0;
                    double pb = b.getPagerank() != null ? b.getPagerank() : 0.0;
                    return Double.compare(pb, pa);
                })
                .toList();

        for (double threshold : PAGERANK_THRESHOLDS) {
            List<CodeIndex> filtered = sorted.stream()
                    .filter(e -> e.isAuthoredByMe()
                            || (e.getPagerank() != null && e.getPagerank() >= threshold))
                    .toList();
            String text = serializeCodeEntries(filtered);
            if (text.length() <= budget) return text;
        }

        return serializeCodeEntries(sorted.stream().filter(CodeIndex::isAuthoredByMe).toList());
    }

    private String serializeCodeEntries(List<CodeIndex> entries) {
        StringBuilder sb = new StringBuilder();
        for (CodeIndex e : entries) {
            sb.append(e.isAuthoredByMe() ? "[authored] " : "          ");
            sb.append(e.getFqn());
            if (e.getPagerank() != null) {
                sb.append(String.format(" (pagerank=%.2f)", e.getPagerank()));
            }
            sb.append(" → ").append(e.getFilePath());
            sb.append("\n");
        }
        return sb.toString();
    }
}

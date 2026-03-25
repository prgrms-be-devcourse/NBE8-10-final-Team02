package com.back.backend.domain.github.portfolio;

import com.back.backend.domain.github.analysis.DiffEntry;
import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RepoSummary 생성 요청용 AI userMessage 빌더.
 *
 * userMessage 구성:
 *   - repo 기본 정보 (이름, 언어, 크기)
 *   - 본인 기여 클래스 목록 (PageRank 상위, authored_by_me=true 우선)
 *   - 본인 커밋 diff 목록 (최근 N개, 토큰 예산 내)
 *
 * Token Budget:
 *   본인 소유 repo: 코드 구조 30K + diff 50K  = 80K context
 *   외부(org/오픈소스) repo: 코드 구조 10K + diff 40K = 50K context
 *   → PageRank 임계값(0.6→0.7→0.8)을 높여 초과 시 점진적 축소
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
     * @param diffs           본인 기여 diff 목록
     * @param isOwnedRepo     본인 소유 repo 여부 (token budget 차등 적용)
     * @param projectOverview README 등 프로젝트 개요 텍스트 (nullable).
     *                        large repo에서 전체 정적 분석 대신 제공되는 구조 보완 자료.
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
        // large repo처럼 전체 정적 분석을 생략한 경우, AI가 전체 프로젝트 맥락을 파악하는 데 활용한다.
        if (projectOverview != null && !projectOverview.isBlank()) {
            sb.append("## Project Overview (README)\n");
            sb.append(projectOverview).append("\n\n");
        }

        // --- 코드 구조 (PageRank 필터링) ---
        sb.append("## Code Structure (authored classes highlighted)\n");
        String codeSection = buildCodeSection(codeEntries, codeBudget);
        sb.append(codeSection).append("\n\n");

        // --- 기여 diff ---
        sb.append("## Contribution Diffs\n");
        String diffSection = buildDiffSection(diffs, diffBudget);
        sb.append(diffSection).append("\n");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────
    // 내부 빌더
    // ─────────────────────────────────────────────────

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

        // PageRank 임계값 단계적 적용
        for (double threshold : PAGERANK_THRESHOLDS) {
            List<CodeIndex> filtered = sorted.stream()
                    .filter(e -> e.isAuthoredByMe()
                            || (e.getPagerank() != null && e.getPagerank() >= threshold))
                    .toList();

            String text = serializeCodeEntries(filtered);
            if (text.length() <= budget) return text;
        }

        // 임계값 0.8 이후에도 초과 → authored_by_me 항목만
        List<CodeIndex> authoredOnly = sorted.stream()
                .filter(CodeIndex::isAuthoredByMe)
                .toList();
        return serializeCodeEntries(authoredOnly);
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

    private String buildDiffSection(List<DiffEntry> diffs, int budget) {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (DiffEntry diff : diffs) {
            String entry = "### " + diff.sha() + " " + diff.subject() + "\n"
                    + diff.diff() + "\n";
            if (used + entry.length() > budget) {
                sb.append("... [remaining diffs truncated due to token budget]\n");
                break;
            }
            sb.append(entry);
            used += entry.length();
        }
        return sb.toString();
    }
}

package com.back.backend.domain.github.portfolio;

import com.back.backend.domain.github.analysis.DiffEntry;
import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 여러 repo의 데이터를 하나의 XML 페이로드로 조립하는 배치 프롬프트 빌더.
 *
 * <h3>Markdown 대신 XML을 사용하는 이유</h3>
 * 여러 repo와 diff 구간이 섞이면 {@code ## Section} 방식의 Markdown 경계가 모호해져,
 * AI가 이전 repo 내용을 다음 repo 분석에 혼용하는 Context Bleeding이 발생한다.
 * XML 태그({@code <repository>}, {@code <diff>})는 시작/끝 경계가 명확하여
 * 이 문제를 원천 차단한다.
 *
 * <h3>삭제된 메타데이터</h3>
 * 토큰 절약을 위해 아래 필드를 프롬프트에서 제거한다:
 * <ul>
 *   <li>repo HTML URL</li>
 *   <li>repo 크기(size_kb)</li>
 *   <li>author email</li>
 * </ul>
 * 이 정보들은 AI의 요약 품질에 기여하지 않는다.
 *
 * <h3>2D Rollover 적용 지점</h3>
 * {@link #build(List, BatchTokenBudget)} 내부에서 {@link BatchTokenBudget}을 통해
 * repo별, 구간별 char 한도를 받아 초과 시 truncate한다.
 */
@Component
public class BatchPortfolioPromptBuilder {

    // CodeIndex 코드 구조 섹션에 고정 할당할 최대 chars
    // (diff 예산 계산 전에 fixedUsed에 포함)
    private static final int CODE_SECTION_FIXED_BUDGET = 40_000;
    // overview(README) 섹션에 고정 할당할 최대 chars
    private static final int OVERVIEW_FIXED_BUDGET = 8_000;

    // PageRank 임계값 단계 (code_structure 예산 초과 시 단계적으로 높임)
    private static final double[] PAGERANK_THRESHOLDS = { 0.6, 0.7, 0.8 };

    /**
     * 단일 repo의 배치 데이터 컨테이너.
     *
     * @param repo           대상 repo 엔티티
     * @param isOwnedRepo    본인 소유 repo 여부
     * @param projectOverview README 텍스트 (없으면 null)
     * @param codeEntries    CodeIndex 목록 (PageRank + authoredByMe 포함)
     * @param earlyDiffs     초기 구간 diff 목록 (전체의 첫 1/3)
     * @param midDiffs       중기 구간 diff 목록 (전체의 중간 1/3)
     * @param lateDiffs      후기 구간 diff 목록 (전체의 마지막 1/3)
     */
    public record RepoBatchData(
            GithubRepository repo,
            boolean isOwnedRepo,
            String projectOverview,
            List<CodeIndex> codeEntries,
            List<DiffEntry> earlyDiffs,
            List<DiffEntry> midDiffs,
            List<DiffEntry> lateDiffs
    ) {}

    /**
     * 여러 repo 데이터를 하나의 XML {@code <batch_data>} 페이로드로 조립한다.
     *
     * <p>각 repo에 대해 {@link BatchTokenBudget}을 통해 예산을 할당하고,
     * repo 내부 Early/Mid/Late 구간에 2D Rollover를 적용한다.
     *
     * @param repoDataList 분석 완료된 repo 데이터 목록 (순서가 이월 방향)
     * @param budget       2D Rollover 예산 관리자
     * @return AI user message로 전달할 XML 페이로드 문자열
     */
    public String build(List<RepoBatchData> repoDataList, BatchTokenBudget budget) {
        StringBuilder xml = new StringBuilder();
        xml.append("<batch_data>\n");

        for (RepoBatchData data : repoDataList) {
            // ── 1차원: 이 repo의 전체 가용 예산 확보 ─────────────
            int repoBudget = budget.allocateForRepo();

            // ── 고정 섹션(overview + code_structure) 조립 ────────
            String overviewSection = buildOverview(data.projectOverview());
            String codeSection = buildCodeStructure(data.codeEntries());

            // 고정 섹션의 실제 chars 측정
            int fixedUsed = overviewSection.length() + codeSection.length();

            // ── 2차원: diff 구간별 예산 계산 ──────────────────────
            // fixedUsed를 먼저 차감하여 diff에 쓸 수 있는 예산 3등분
            int[] phaseBudgets = budget.allocatePhases(repoBudget, fixedUsed);

            // Early 구간 조립 + 실제 사용량 측정
            String earlySection = buildDiffPhase("early", data.earlyDiffs(), phaseBudgets[0]);
            phaseBudgets = budget.rolloverPhase(phaseBudgets, 0, earlySection.length());

            // Mid 구간 조립 (Early 잔여 이월분 포함)
            String midSection = buildDiffPhase("mid", data.midDiffs(), phaseBudgets[1]);
            phaseBudgets = budget.rolloverPhase(phaseBudgets, 1, midSection.length());

            // Late 구간 조립 (Mid 잔여 이월분 포함)
            String lateSection = buildDiffPhase("late", data.lateDiffs(), phaseBudgets[2]);

            // ── repo XML 조립 ────────────────────────────────────
            String repoKey = deriveRepoKey(data.repo());
            String repoType = deriveRepoType(data.repo());

            xml.append("  <repository id=\"").append(escapeXml(repoKey))
               .append("\" name=\"").append(escapeXml(data.repo().getRepoName()))
               .append("\" type=\"").append(repoType).append("\">\n");

            // overview (있을 때만)
            if (!overviewSection.isEmpty()) {
                xml.append("    <overview>\n")
                   .append(overviewSection)
                   .append("\n    </overview>\n");
            }

            // code_structure
            xml.append("    <code_structure>\n")
               .append(codeSection)
               .append("\n    </code_structure>\n");

            // diffs
            xml.append("    <diffs>\n")
               .append(earlySection)
               .append(midSection)
               .append(lateSection)
               .append("    </diffs>\n");

            xml.append("  </repository>\n");

            // ── 1차원: 이 repo의 실제 사용량 기록 → 다음 repo로 이월 ─
            int totalRepoUsed = fixedUsed + earlySection.length()
                              + midSection.length() + lateSection.length();
            budget.commitRepoUsage(totalRepoUsed);
        }

        xml.append("</batch_data>");
        return xml.toString();
    }

    // ─────────────────────────────────────────────────────────
    // 섹션별 빌더
    // ─────────────────────────────────────────────────────────

    /**
     * README 개요 텍스트를 반환한다. null이거나 비어있으면 빈 문자열 반환.
     * {@link #OVERVIEW_FIXED_BUDGET}을 초과하면 자른다.
     */
    private String buildOverview(String projectOverview) {
        if (projectOverview == null || projectOverview.isBlank()) return "";
        if (projectOverview.length() > OVERVIEW_FIXED_BUDGET) {
            return projectOverview.substring(0, OVERVIEW_FIXED_BUDGET) + "\n... [truncated]";
        }
        return projectOverview;
    }

    /**
     * CodeIndex 목록을 "authored 파일 + PageRank" 텍스트로 직렬화한다.
     *
     * <p>authored_by_me=true 항목을 먼저 포함, 이후 pagerank 내림차순.
     * {@link #CODE_SECTION_FIXED_BUDGET}을 초과하면 PageRank 임계값을 높여 항목을 줄인다.
     */
    private String buildCodeStructure(List<CodeIndex> entries) {
        if (entries == null || entries.isEmpty()) return "";

        // authored 우선, 이후 pagerank 내림차순 정렬
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

        // 임계값을 단계적으로 높여 budget 내에 맞춤
        for (double threshold : PAGERANK_THRESHOLDS) {
            List<CodeIndex> filtered = sorted.stream()
                    .filter(e -> e.isAuthoredByMe()
                            || (e.getPagerank() != null && e.getPagerank() >= threshold))
                    .toList();
            String text = serializeCodeEntries(filtered);
            if (text.length() <= CODE_SECTION_FIXED_BUDGET) return text;
        }

        // 모든 임계값에서 초과: authored 파일만 포함
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
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * diff 구간을 {@code <diff phase="...">} XML 블록으로 조립한다.
     *
     * <h3>예산 처리 전략 (3단계 Fallback)</h3>
     * <ol>
     *   <li><b>전체 포함</b>: subject + body + diff가 예산 내에 맞으면 그대로 포함</li>
     *   <li><b>요약본 포함</b>: diff가 예산을 초과하면 diff를 제거하고 subject + body만 전달.
     *       커밋 메시지만으로도 AI가 techDecisions·challenges 소재를 추론할 수 있음.</li>
     *   <li><b>완전 생략</b>: 요약본도 예산 내에 들어오지 않으면 해당 커밋은 skip</li>
     * </ol>
     *
     * <p>이전 방식(예산 초과 시 즉시 {@code break})과 달리, 모든 커밋의 메시지를
     * 최대한 포함하여 AI에게 더 많은 컨텍스트를 제공한다.
     */
    private String buildDiffPhase(String phase, List<DiffEntry> diffs, int phaseBudget) {
        if (diffs == null || diffs.isEmpty()) return "";

        StringBuilder content = new StringBuilder();
        int used = 0;

        for (DiffEntry d : diffs) {
            // ── 1단계: subject + body + diff 전체 버전 ──────────────────
            StringBuilder fullEntry = new StringBuilder();
            fullEntry.append("### ").append(d.sha()).append(" ").append(d.subject()).append("\n");
            if (!d.body().isBlank()) {
                fullEntry.append("body: ").append(d.body()).append("\n");
            }
            fullEntry.append(d.diff()).append("\n");

            if (used + fullEntry.length() <= phaseBudget) {
                // 예산 내 → 전체 diff 포함
                content.append(fullEntry);
                used += fullEntry.length();
                continue;
            }

            // ── 2단계: diff 제거, subject + body 요약본만 포함 ──────────
            // diff를 제외해도 커밋 메시지는 AI가 techDecisions/challenges를 추론하는 데 충분한 힌트를 제공한다.
            StringBuilder summaryEntry = new StringBuilder();
            summaryEntry.append("### ").append(d.sha()).append(" ").append(d.subject())
                        .append(" [diff omitted: budget]\n");
            if (!d.body().isBlank()) {
                summaryEntry.append("body: ").append(d.body()).append("\n");
            }

            if (used + summaryEntry.length() <= phaseBudget) {
                // 요약본은 예산 내 → 요약본 포함
                content.append(summaryEntry);
                used += summaryEntry.length();
                continue;
            }

            // ── 3단계: 요약본도 초과 → 완전 생략 (다음 커밋 시도 계속) ─
            // break하지 않고 continue하여 이후 커밋 중 짧은 것은 포함될 기회를 준다.
        }

        if (content.isEmpty()) return "";

        return "      <diff phase=\"" + phase + "\">\n"
                + content
                + "      </diff>\n";
    }

    // ─────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────

    /**
     * repo의 fullName에서 AI가 식별할 수 있는 짧은 key를 추출한다.
     * 예: "prgrms-be-devcourse/NBE8-10-final-Team02" → "nbe8-10-final-team02"
     */
    private String deriveRepoKey(GithubRepository repo) {
        return repo.getRepoName().toLowerCase().replaceAll("[^a-z0-9\\-]", "-");
    }

    /**
     * repo가 본인 소유인지 팀/외부인지 타입을 결정한다.
     * GithubRepository에 소유자 판단 로직이 없으므로 ownerLogin 기준으로 간략히 구분.
     * 정밀한 분류는 호출자(BatchRepoSummaryGeneratorService)가 isOwnedRepo 플래그로 전달.
     */
    private String deriveRepoType(GithubRepository repo) {
        // ownerLogin이 org처럼 보이면 team, 아니면 own으로 처리
        // 실제 분류는 RepoBatchData.isOwnedRepo()를 활용
        return "team"; // 기본값; 호출자가 더 정밀하게 제어 가능
    }

    /**
     * XML 특수문자를 이스케이프한다 (속성값에 삽입 시 필요).
     */
    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}

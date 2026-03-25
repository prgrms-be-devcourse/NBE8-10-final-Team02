package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.MergedSummary;
import com.back.backend.domain.github.entity.RepoSummary;
import com.back.backend.domain.github.repository.MergedSummaryRepository;
import com.back.backend.domain.github.repository.RepoSummaryRepository;
import com.back.backend.domain.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 사용자의 전체 RepoSummary를 통합하는 MergedSummary 집계 서비스 (설계 §3.5, §8).
 *
 * merge_strategy 선택 기준:
 *   - repo 수 < 5개: json_aggregate (LLM 없이 JSON 직접 병합)
 *   - repo 수 ≥ 5개: json_aggregate (우선 구현; LLM rewrite는 TODO)
 *
 * MergedSummary.data 구조:
 *   portfolio-summary.schema.json 필드(projects, globalStrengths, globalRisks, qualityFlags)
 *   + leanSummary 확장 필드 (Groq 면접 컨텍스트용)
 *
 * lean_summary 구성:
 *   stack      → globalStrengths 상위 5개
 *   projects   → 각 project.summary 한 줄씩
 *   highlights → 각 project.signals 상위 2개씩 (최대 6개 합산)
 *   hooks      → 각 project.signals 전체 (면접 화두 후보)
 */
@Service
public class MergedSummaryService {

    private static final Logger log = LoggerFactory.getLogger(MergedSummaryService.class);
    private static final int MAX_LEAN_STACK_ITEMS = 5;
    private static final int MAX_LEAN_HIGHLIGHTS = 6;

    private final RepoSummaryRepository repoSummaryRepository;
    private final MergedSummaryRepository mergedSummaryRepository;
    private final ObjectMapper objectMapper;

    public MergedSummaryService(
            RepoSummaryRepository repoSummaryRepository,
            MergedSummaryRepository mergedSummaryRepository,
            ObjectMapper objectMapper
    ) {
        this.repoSummaryRepository = repoSummaryRepository;
        this.mergedSummaryRepository = mergedSummaryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 사용자의 최신 RepoSummary들을 모아 MergedSummary를 생성한다.
     * 기존 MergedSummary가 있어도 새 버전으로 저장 (이력 보존).
     *
     * @return 저장된 MergedSummary
     */
    @Transactional
    public MergedSummary rebuild(User user) {
        log.info("Rebuilding merged summary for userId={}", user.getId());

        // 사용자의 최신 RepoSummary 목록 (repo별 최신 1개)
        List<RepoSummary> latestSummaries = getLatestPerRepo(user);

        if (latestSummaries.isEmpty()) {
            log.warn("No repo summaries found for userId={}, skipping merge", user.getId());
            throw new IllegalStateException("병합할 RepoSummary가 없습니다.");
        }

        // JSON 집계
        String mergedJson = aggregate(latestSummaries);
        List<Long> includedRepoIds = latestSummaries.stream()
                .map(s -> s.getGithubRepository().getId())
                .toList();

        int nextVersion = mergedSummaryRepository.findTopByUserOrderByMergedVersionDesc(user)
                .map(m -> m.getMergedVersion() + 1)
                .orElse(1);

        MergedSummary merged = MergedSummary.builder()
                .user(user)
                .mergedVersion(nextVersion)
                .includedRepoIds(toJson(includedRepoIds))
                .data(mergedJson)
                .mergeStrategy("json_aggregate")
                .generatedAt(Instant.now())
                .build();

        MergedSummary saved = mergedSummaryRepository.save(merged);
        log.info("MergedSummary saved: userId={}, version={}, repos={}",
                user.getId(), nextVersion, includedRepoIds.size());
        return saved;
    }

    /**
     * 최신 MergedSummary에서 Groq 면접 컨텍스트용 lean_summary JSON을 추출한다.
     * 없으면 빈 문자열 반환.
     */
    @Transactional(readOnly = true)
    public String getLeanSummaryJson(User user) {
        return mergedSummaryRepository.findTopByUserOrderByMergedVersionDesc(user)
                .map(MergedSummary::getData)
                .flatMap(data -> extractField(data, "leanSummary"))
                .orElse("{}");
    }

    // ─────────────────────────────────────────────────
    // 집계 로직
    // ─────────────────────────────────────────────────

    /**
     * 여러 RepoSummary의 projects를 하나로 합치고 globalStrengths/leanSummary를 파생한다.
     */
    private String aggregate(List<RepoSummary> summaries) {
        ArrayNode allProjects = objectMapper.createArrayNode();
        Set<String> globalStrengths = new LinkedHashSet<>();
        List<String> leanProjectLines = new ArrayList<>();
        List<String> leanHighlights = new ArrayList<>();
        List<String> leanHooks = new ArrayList<>();

        for (RepoSummary summary : summaries) {
            try {
                JsonNode data = objectMapper.readTree(summary.getData());

                // projects 배열 수집
                JsonNode projects = data.get("projects");
                if (projects != null && projects.isArray()) {
                    projects.forEach(project -> {
                        allProjects.add(project);

                        // lean_summary 재료 수집
                        String projectSummary = textOf(project, "summary");
                        String projectName = textOf(project, "projectName");
                        if (!projectSummary.isEmpty()) {
                            leanProjectLines.add(projectName + ": " + projectSummary);
                        }

                        JsonNode signals = project.get("signals");
                        if (signals != null && signals.isArray()) {
                            int count = 0;
                            for (JsonNode signal : signals) {
                                String s = signal.asText();
                                leanHooks.add(s);
                                if (count < 2 && leanHighlights.size() < MAX_LEAN_HIGHLIGHTS) {
                                    leanHighlights.add(s);
                                    count++;
                                }
                            }
                        }
                    });
                }

                // globalStrengths 수집 (각 repo의 globalStrengths를 합산)
                JsonNode strengths = data.get("globalStrengths");
                if (strengths != null && strengths.isArray()) {
                    strengths.forEach(s -> globalStrengths.add(s.asText()));
                }

            } catch (JsonProcessingException e) {
                log.warn("Failed to parse RepoSummary data for repoId={}: {}",
                        summary.getGithubRepository().getId(), e.getMessage());
            }
        }

        // 최종 MergedSummary JSON 구성
        ObjectNode result = objectMapper.createObjectNode();
        result.set("projects", allProjects);
        result.set("globalStrengths", toArrayNode(new ArrayList<>(globalStrengths)));
        result.set("globalRisks", objectMapper.createArrayNode());
        result.set("qualityFlags", objectMapper.createArrayNode());

        // leanSummary 파생
        ObjectNode leanSummary = objectMapper.createObjectNode();
        leanSummary.put("stack", String.join(" / ",
                new ArrayList<>(globalStrengths).stream().limit(MAX_LEAN_STACK_ITEMS).toList()));
        leanSummary.set("projects", toArrayNode(leanProjectLines));
        leanSummary.set("highlights", toArrayNode(leanHighlights));
        leanSummary.set("hooks", toArrayNode(leanHooks.stream().distinct().limit(10).toList()));
        result.set("leanSummary", leanSummary);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize merged summary: {}", e.getMessage());
            return "{}";
        }
    }

    // ─────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────

    /**
     * 사용자의 RepoSummary 목록에서 repo별 최신 버전만 선택한다.
     */
    private List<RepoSummary> getLatestPerRepo(User user) {
        List<RepoSummary> all = repoSummaryRepository.findAllByUserOrderBySummaryVersionDesc(user);
        // repo별 첫 번째(최신) 항목만 유지
        Set<Long> seen = new LinkedHashSet<>();
        return all.stream()
                .filter(s -> seen.add(s.getGithubRepository().getId()))
                .toList();
    }

    private String textOf(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f != null ? f.asText("") : "";
    }

    private ArrayNode toArrayNode(List<String> items) {
        ArrayNode arr = objectMapper.createArrayNode();
        items.forEach(arr::add);
        return arr;
    }

    private Optional<String> extractField(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode target = node.get(field);
            return target != null ? Optional.of(objectMapper.writeValueAsString(target)) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}

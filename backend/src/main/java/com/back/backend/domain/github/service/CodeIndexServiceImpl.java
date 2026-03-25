package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.repository.CodeIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CodeIndexService 구현체.
 *
 * 소스 원문 읽기:
 *   OCI 파일시스템의 clone 경로({repoBasePath}/{userId}/{repoId}/) + code_index.file_path로 파일을 읽는다.
 *
 * 소유자 확인:
 *   GithubRepository → GithubConnection → User.id 경로로 userId를 확인한다.
 *   단, 소스 읽기는 내부 서비스 호출이므로 호출자가 userId를 직접 전달한다.
 */
@Service
public class CodeIndexServiceImpl implements CodeIndexService {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexServiceImpl.class);

    private final CodeIndexRepository codeIndexRepository;
    private final RepoCloneService repoCloneService;
    private final ObjectMapper objectMapper;

    public CodeIndexServiceImpl(
            CodeIndexRepository codeIndexRepository,
            RepoCloneService repoCloneService,
            ObjectMapper objectMapper
    ) {
        this.codeIndexRepository = codeIndexRepository;
        this.repoCloneService = repoCloneService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void buildIndex(GithubRepository repo, Long userId,
                           List<AnalysisNode> nodes, Set<String> authoredFiles,
                           Map<String, Double> pagerankMap) {
        log.info("Building code index: repoId={}, nodes={}", repo.getId(), nodes.size());

        // 기존 인덱스 교체 (전체 재분석)
        codeIndexRepository.deleteByGithubRepository(repo);

        List<CodeIndex> entries = new ArrayList<>();

        for (AnalysisNode node : nodes) {
            String methodsJson = serializeMethods(node.methods());
            double pagerank = pagerankMap.getOrDefault(node.fqn(), 0.0);
            boolean authoredByMe = authoredFiles.contains(node.filePath());

            entries.add(CodeIndex.builder()
                    .githubRepository(repo)
                    .fqn(node.fqn())
                    .filePath(node.filePath())
                    .locStart(node.locStart())
                    .locEnd(node.locEnd())
                    .nodeType(node.nodeType())
                    .pagerank(pagerank)
                    .authoredByMe(authoredByMe)
                    .methods(methodsJson)
                    .analyzedAt(Instant.now())
                    .build());
        }

        codeIndexRepository.saveAll(entries);
        log.info("Code index built: repoId={}, saved={}", repo.getId(), entries.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodeIndex> getTopByPageRank(GithubRepository repo, double minPageRank) {
        return codeIndexRepository
                .findByGithubRepositoryAndPagerankGreaterThanEqualOrderByPagerankDesc(repo, minPageRank);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodeIndex> getAuthoredEntries(GithubRepository repo) {
        return codeIndexRepository.findByGithubRepositoryAndAuthoredByMeTrue(repo);
    }

    @Override
    @Transactional(readOnly = true)
    public String getClassSource(GithubRepository repo, Long userId, String fqn) {
        // 정확 매칭 시도
        var exact = codeIndexRepository.findByGithubRepositoryAndFqn(repo, fqn);
        if (exact.isPresent()) {
            return readLines(userId, repo.getId(), exact.get().getFilePath(),
                    exact.get().getLocStart(), exact.get().getLocEnd());
        }

        // 부분 매칭 (클래스명만 알 때)
        List<CodeIndex> candidates = codeIndexRepository
                .findByGithubRepositoryAndFqnContaining(repo, fqn);
        if (candidates.isEmpty()) {
            log.debug("No class found for fqn={} in repoId={}", fqn, repo.getId());
            return "";
        }
        if (candidates.size() == 1) {
            CodeIndex c = candidates.get(0);
            return readLines(userId, repo.getId(), c.getFilePath(), c.getLocStart(), c.getLocEnd());
        }

        // 복수 후보: 후보 목록을 텍스트로 반환 (Gemini가 선택하도록)
        StringBuilder sb = new StringBuilder("여러 후보가 있습니다. 구체적인 FQN을 지정해주세요:\n");
        candidates.forEach(c -> sb.append("- ").append(c.getFqn()).append("\n"));
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public String getFileSource(GithubRepository repo, Long userId, String filePath) {
        // 정확 매칭
        var exact = codeIndexRepository.findByGithubRepositoryAndFilePath(repo, filePath);
        if (exact.isPresent()) {
            return readFile(userId, repo.getId(), filePath);
        }

        // 부분 매칭 (파일명만 알 때)
        List<CodeIndex> candidates = codeIndexRepository
                .findByGithubRepositoryAndFilePathContaining(repo, filePath);
        if (candidates.isEmpty()) return "";
        if (candidates.size() == 1) {
            return readFile(userId, repo.getId(), candidates.get(0).getFilePath());
        }

        StringBuilder sb = new StringBuilder("여러 파일 후보가 있습니다:\n");
        candidates.forEach(c -> sb.append("- ").append(c.getFilePath()).append("\n"));
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public String getMethodSource(GithubRepository repo, Long userId, String fqn, String methodName) {
        var classEntry = codeIndexRepository.findByGithubRepositoryAndFqn(repo, fqn);
        if (classEntry.isEmpty()) return "";

        CodeIndex cls = classEntry.get();
        if (cls.getMethods() == null) return "";

        // methods JSON에서 methodName 매칭
        try {
            List<Map<String, Object>> methods = objectMapper.readValue(cls.getMethods(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            for (Map<String, Object> method : methods) {
                String name = (String) method.get("name");
                String sig  = (String) method.get("signature");
                if (methodName.equals(name) || (sig != null && sig.contains(methodName))) {
                    int start = ((Number) method.getOrDefault("locStart", cls.getLocStart())).intValue();
                    int end   = ((Number) method.getOrDefault("locEnd",   cls.getLocEnd())).intValue();
                    return readLines(userId, repo.getId(), cls.getFilePath(), start, end);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse methods JSON for fqn={}: {}", fqn, e.getMessage());
        }
        return "";
    }

    @Override
    @Transactional
    public void deleteByRepository(GithubRepository repo) {
        codeIndexRepository.deleteByGithubRepository(repo);
        log.info("Code index deleted for repoId={}", repo.getId());
    }

    // ─────────────────────────────────────────────────
    // 파일시스템 읽기
    // ─────────────────────────────────────────────────

    /**
     * OCI 파일시스템의 clone에서 특정 라인 범위를 읽는다.
     * locStart/locEnd가 0이면 파일 전체를 반환한다.
     */
    private String readLines(Long userId, Long repositoryId,
                              String relativeFilePath, Integer locStart, Integer locEnd) {
        Path repoRoot = repoCloneService.buildRepoPath(userId, repositoryId);
        Path filePath = repoRoot.resolve(relativeFilePath);

        if (!Files.exists(filePath)) {
            log.debug("Source file not found: {}", filePath);
            return "";
        }

        try {
            if (locStart == null || locEnd == null || locStart == 0) {
                return Files.readString(filePath);
            }

            List<String> lines = Files.readAllLines(filePath);
            int from = Math.max(0, locStart - 1);        // 1-indexed → 0-indexed
            int to   = Math.min(lines.size(), locEnd);   // exclusive end
            return String.join("\n", lines.subList(from, to));

        } catch (IOException e) {
            log.warn("Failed to read source file: {}, reason: {}", filePath, e.getMessage());
            return "";
        }
    }

    private String readFile(Long userId, Long repositoryId, String relativeFilePath) {
        return readLines(userId, repositoryId, relativeFilePath, null, null);
    }

    // ─────────────────────────────────────────────────
    // 직렬화 유틸
    // ─────────────────────────────────────────────────

    private String serializeMethods(List<AnalysisNode.MethodInfo> methods) {
        if (methods == null || methods.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(methods);
        } catch (Exception e) {
            log.warn("Failed to serialize methods: {}", e.getMessage());
            return null;
        }
    }
}

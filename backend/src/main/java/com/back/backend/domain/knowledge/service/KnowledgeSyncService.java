package com.back.backend.domain.knowledge.service;

import com.back.backend.domain.knowledge.client.KnowledgeGitHubClient;
import com.back.backend.domain.knowledge.dto.KnowledgeSyncResult;
import com.back.backend.domain.knowledge.parser.KnowledgeParser;
import com.back.backend.domain.knowledge.source.KnowledgeSource;
import com.back.backend.domain.knowledge.source.KnowledgeSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class KnowledgeSyncService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncService.class);

    private final KnowledgeGitHubClient githubClient;
    private final KnowledgeSyncTransactionHelper txHelper;

    public KnowledgeSyncService(
            KnowledgeGitHubClient githubClient,
            KnowledgeSyncTransactionHelper txHelper
    ) {
        this.githubClient = githubClient;
        this.txHelper = txHelper;
    }

    public KnowledgeSyncResult syncAll() {
        int imported = 0, updated = 0, skipped = 0, failed = 0, deleted = 0;

        for (KnowledgeSource source : KnowledgeSourceRegistry.SOURCES) {
            log.info("Syncing source: {}", source.key());
            Map<String, String> contents = fetchContents(source);
            log.info("source={} → {} files fetched", source.key(), contents.size());

            for (Map.Entry<String, String> entry : contents.entrySet()) {
                String filePath = entry.getKey();
                String rawContent = entry.getValue();

                List<KnowledgeParser.ParsedItem> parsedItems;
                try {
                    parsedItems = source.parser().parse(rawContent, source);
                } catch (Exception e) {
                    log.warn("Parse failed source={} filePath={}: {}", source.key(), filePath, e.getMessage());
                    failed++;
                    continue;
                }

                if (parsedItems.isEmpty()) {
                    log.info("No items parsed source={} filePath={} (contentLen={})", source.key(), filePath, rawContent.length());
                    continue;
                }
                log.debug("Parsed {} items from source={} filePath={}", parsedItems.size(), source.key(), filePath);

                // 파일 내 전체 타이틀 먼저 수집 → 삭제 기준으로 사용
                Set<String> parsedTitles = new HashSet<>();
                for (KnowledgeParser.ParsedItem item : parsedItems) {
                    parsedTitles.add(item.title());
                }

                for (KnowledgeParser.ParsedItem item : parsedItems) {
                    try {
                        KnowledgeSyncTransactionHelper.SyncAction action =
                                txHelper.upsertItem(source.key(), filePath, item);
                        switch (action) {
                            case IMPORTED -> imported++;
                            case UPDATED  -> updated++;
                            case SKIPPED  -> skipped++;
                        }
                    } catch (Exception e) {
                        log.warn("Upsert failed title='{}' source={}: {}", item.title(), source.key(), e.getMessage());
                        failed++;
                    }
                }

                try {
                    int d = txHelper.deleteRemovedItems(source.key(), filePath, parsedTitles);
                    deleted += d;
                    if (d > 0) log.info("Deleted {} removed items from source={} filePath={}", d, source.key(), filePath);
                } catch (Exception e) {
                    log.warn("Delete failed source={} filePath={}: {}", source.key(), filePath, e.getMessage());
                }
            }
        }

        log.info("Sync done — imported={} updated={} skipped={} failed={} deleted={}",
                imported, updated, skipped, failed, deleted);
        return new KnowledgeSyncResult(imported, updated, skipped, failed, deleted);
    }

    private Map<String, String> fetchContents(KnowledgeSource source) {
        if (source instanceof KnowledgeSource.GithubSource gs) {
            Map<String, String> result = new LinkedHashMap<>();
            for (String path : gs.paths()) {
                result.putAll(githubClient.fetchContent(gs.repo(), path));
            }
            return result;
        }
        if (source instanceof KnowledgeSource.LocalFileSource lfs) {
            try {
                ClassPathResource resource = new ClassPathResource(lfs.classpathPath());
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                return Map.of(lfs.classpathPath(), content);
            } catch (IOException e) {
                log.warn("Failed to read local file: {}", lfs.classpathPath());
                return Map.of();
            }
        }
        return Map.of();
    }
}

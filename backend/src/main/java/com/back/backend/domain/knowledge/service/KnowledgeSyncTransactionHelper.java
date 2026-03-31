package com.back.backend.domain.knowledge.service;

import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.knowledge.entity.KnowledgeItemTag;
import com.back.backend.domain.knowledge.entity.KnowledgeTag;
import com.back.backend.domain.knowledge.parser.KeywordTagExtractor;
import com.back.backend.domain.knowledge.parser.KnowledgeParser;
import com.back.backend.domain.knowledge.repository.KnowledgeItemRepository;
import com.back.backend.domain.knowledge.repository.KnowledgeItemTagRepository;
import com.back.backend.domain.knowledge.repository.KnowledgeTagRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Optional;

/**
 * sync의 DB 쓰기 작업을 아이템 단위 트랜잭션으로 격리한다.
 * KnowledgeSyncService에서 직접 호출하면 Spring AOP 프록시를 통하지 않으므로
 * 별도 컴포넌트로 분리한다.
 */
@Component
public class KnowledgeSyncTransactionHelper {

    private final KnowledgeItemRepository itemRepo;
    private final KnowledgeTagRepository tagRepo;
    private final KnowledgeItemTagRepository itemTagRepo;

    public KnowledgeSyncTransactionHelper(
            KnowledgeItemRepository itemRepo,
            KnowledgeTagRepository tagRepo,
            KnowledgeItemTagRepository itemTagRepo
    ) {
        this.itemRepo = itemRepo;
        this.tagRepo = tagRepo;
        this.itemTagRepo = itemTagRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncAction upsertItem(String sourceKey, String filePath, KnowledgeParser.ParsedItem parsed) {
        String hash = sha256(parsed.content());
        Optional<KnowledgeItem> existing =
                itemRepo.findBySourceKeyAndFilePathAndTitle(sourceKey, filePath, parsed.title());

        KnowledgeItem item;
        SyncAction action;

        if (existing.isEmpty()) {
            item = itemRepo.save(
                    KnowledgeItem.create(sourceKey, filePath, parsed.title(), parsed.content(), hash));
            action = SyncAction.IMPORTED;
        } else {
            item = existing.get();
            if (item.getContentHash().equals(hash)) return SyncAction.SKIPPED;
            item.update(parsed.content(), hash);
            action = SyncAction.UPDATED;
        }

        for (String tagName : parsed.autoTags()) {
            KnowledgeTag tag = tagRepo.findByName(tagName)
                    .orElseGet(() -> tagRepo.save(
                            KnowledgeTag.create(tagName, KeywordTagExtractor.categoryOf(tagName))));
            if (!itemTagRepo.existsByItemAndTag(item, tag)) {
                itemTagRepo.save(KnowledgeItemTag.of(item, tag));
            }
        }

        return action;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteRemovedItems(String sourceKey, String filePath, Collection<String> parsedTitles) {
        if (parsedTitles.isEmpty()) return 0;
        return itemRepo.deleteBySourceKeyAndFilePathAndTitleNotIn(sourceKey, filePath, parsedTitles);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public enum SyncAction { IMPORTED, UPDATED, SKIPPED }
}

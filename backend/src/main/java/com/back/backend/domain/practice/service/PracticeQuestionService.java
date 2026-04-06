package com.back.backend.domain.practice.service;

import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.knowledge.entity.KnowledgeItemTag;
import com.back.backend.domain.knowledge.repository.KnowledgeItemRepository;
import com.back.backend.domain.knowledge.repository.KnowledgeItemTagRepository;
import com.back.backend.domain.knowledge.repository.KnowledgeTagRepository;
import com.back.backend.domain.practice.dto.response.PracticeQuestionResponse;
import com.back.backend.domain.practice.dto.response.PracticeTagResponse;
import com.back.backend.domain.practice.entity.PracticeQuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PracticeQuestionService {

    private static final String BEHAVIORAL_SOURCE_KEY = "local-behavioral";

    private final KnowledgeItemRepository knowledgeItemRepository;
    private final KnowledgeItemTagRepository knowledgeItemTagRepository;
    private final KnowledgeTagRepository knowledgeTagRepository;

    public PracticeQuestionService(KnowledgeItemRepository knowledgeItemRepository,
                                   KnowledgeItemTagRepository knowledgeItemTagRepository,
                                   KnowledgeTagRepository knowledgeTagRepository) {
        this.knowledgeItemRepository = knowledgeItemRepository;
        this.knowledgeItemTagRepository = knowledgeItemTagRepository;
        this.knowledgeTagRepository = knowledgeTagRepository;
    }

    public Page<PracticeQuestionResponse> getQuestions(List<Long> tagIds, String questionType,
                                                       Pageable pageable) {
        Page<KnowledgeItem> items = findItems(tagIds, questionType, pageable);
        Map<Long, List<PracticeTagResponse>> tagMap = buildTagMap(items.getContent());

        return items.map(item -> PracticeQuestionResponse.of(
                item.getId(),
                item.getTitle(),
                item.getContent(),
                PracticeQuestionType.fromSourceKey(item.getSourceKey()),
                tagMap.getOrDefault(item.getId(), Collections.emptyList())
        ));
    }

    public List<PracticeQuestionResponse> getRandomQuestions(List<Long> tagIds, String questionType,
                                                             int count) {
        List<KnowledgeItem> items = findRandomItems(tagIds, questionType, count);
        Map<Long, List<PracticeTagResponse>> tagMap = buildTagMap(items);

        return items.stream()
                .map(item -> PracticeQuestionResponse.of(
                        item.getId(),
                        item.getTitle(),
                        item.getContent(),
                        PracticeQuestionType.fromSourceKey(item.getSourceKey()),
                        tagMap.getOrDefault(item.getId(), Collections.emptyList())
                ))
                .toList();
    }

    public List<PracticeTagResponse> getTags(String category) {
        if (category != null && !category.isBlank()) {
            return knowledgeTagRepository.findAll().stream()
                    .filter(tag -> category.equals(tag.getCategory()))
                    .map(PracticeTagResponse::from)
                    .toList();
        }
        return knowledgeTagRepository.findAll().stream()
                .map(PracticeTagResponse::from)
                .toList();
    }

    private Page<KnowledgeItem> findItems(List<Long> tagIds, String questionType, Pageable pageable) {
        boolean hasTagFilter = tagIds != null && !tagIds.isEmpty();
        boolean isBehavioral = "behavioral".equalsIgnoreCase(questionType);
        boolean isCs = "cs".equalsIgnoreCase(questionType);

        if (isBehavioral) {
            return hasTagFilter
                    ? knowledgeItemRepository.findByTagIdsAndSourceKey(tagIds, BEHAVIORAL_SOURCE_KEY, pageable)
                    : knowledgeItemRepository.findBySourceKey(BEHAVIORAL_SOURCE_KEY, pageable);
        }
        if (isCs) {
            return hasTagFilter
                    ? knowledgeItemRepository.findByTagIdsAndSourceKeyNot(tagIds, BEHAVIORAL_SOURCE_KEY, pageable)
                    : knowledgeItemRepository.findBySourceKeyNot(BEHAVIORAL_SOURCE_KEY, pageable);
        }
        // 전체
        return hasTagFilter
                ? knowledgeItemRepository.findByTagIds(tagIds, pageable)
                : knowledgeItemRepository.findAll(pageable);
    }

    private List<KnowledgeItem> findRandomItems(List<Long> tagIds, String questionType, int count) {
        boolean hasTagFilter = tagIds != null && !tagIds.isEmpty();
        boolean isBehavioral = "behavioral".equalsIgnoreCase(questionType);
        boolean isCs = "cs".equalsIgnoreCase(questionType);

        if (isBehavioral) {
            return hasTagFilter
                    ? knowledgeItemRepository.findRandomByTagIdsAndSourceKey(tagIds, BEHAVIORAL_SOURCE_KEY, count)
                    : knowledgeItemRepository.findRandomBySourceKey(BEHAVIORAL_SOURCE_KEY, count);
        }
        if (isCs) {
            return hasTagFilter
                    ? knowledgeItemRepository.findRandomByTagIdsAndSourceKeyNot(tagIds, BEHAVIORAL_SOURCE_KEY, count)
                    : knowledgeItemRepository.findRandomBySourceKeyNot(BEHAVIORAL_SOURCE_KEY, count);
        }
        // 전체
        return hasTagFilter
                ? knowledgeItemRepository.findRandomByTagIds(tagIds, count)
                : knowledgeItemRepository.findRandom(count);
    }

    private Map<Long, List<PracticeTagResponse>> buildTagMap(Collection<KnowledgeItem> items) {
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> itemIds = items.stream().map(KnowledgeItem::getId).toList();
        List<KnowledgeItemTag> itemTags = knowledgeItemTagRepository.findAllWithTagByItemIds(itemIds);

        return itemTags.stream()
                .collect(Collectors.groupingBy(
                        kit -> kit.getItem().getId(),
                        Collectors.mapping(
                                kit -> PracticeTagResponse.from(kit.getTag()),
                                Collectors.toList()
                        )
                ));
    }
}

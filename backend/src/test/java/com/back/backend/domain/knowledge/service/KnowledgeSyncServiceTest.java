package com.back.backend.domain.knowledge.service;

import com.back.backend.domain.knowledge.client.KnowledgeGitHubClient;
import com.back.backend.domain.knowledge.dto.KnowledgeSyncResult;
import com.back.backend.domain.knowledge.parser.JsonBehavioralParser;
import com.back.backend.domain.knowledge.parser.KnowledgeParser;
import com.back.backend.domain.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeSyncServiceTest {

    @Mock
    private KnowledgeGitHubClient githubClient;

    @Mock
    private KnowledgeSyncTransactionHelper txHelper;

    private KnowledgeSyncService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeSyncService(githubClient, txHelper);
    }

    @Test
    void fetchContents_readsAllGithubPathsIntoSingleMap() {
        KnowledgeSource.GithubSource source = new KnowledgeSource.GithubSource(
                "test-github",
                "owner/repo",
                List.of("docs/java", "docs/spring"),
                new com.back.backend.domain.knowledge.parser.MarkdownHeadingParser()
        );
        given(githubClient.fetchContent("owner/repo", "docs/java"))
                .willReturn(Map.of("docs/java/README.md", "## Java\n충분히 긴 설명입니다. Java와 Spring을 함께 설명합니다."));
        given(githubClient.fetchContent("owner/repo", "docs/spring"))
                .willReturn(Map.of("docs/spring/README.md", "## Spring\n충분히 긴 설명입니다. Spring Boot JPA 테스트 내용입니다."));

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) ReflectionTestUtils.invokeMethod(service, "fetchContents", source);

        assertThat(result).containsOnlyKeys("docs/java/README.md", "docs/spring/README.md");
    }

    @Test
    void fetchContents_readsLocalClasspathFile() {
        KnowledgeSource.LocalFileSource source = new KnowledgeSource.LocalFileSource(
                "local-behavioral",
                "data/behavioral-questions.json",
                new JsonBehavioralParser()
        );

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) ReflectionTestUtils.invokeMethod(service, "fetchContents", source);

        assertThat(result).containsKey("data/behavioral-questions.json");
        assertThat(result.get("data/behavioral-questions.json")).contains("\"variations\"");
    }

    @Test
    void syncAll_countsImportedUpdatedSkippedAndPassesParsedTitlesToDelete() throws Exception {
        given(githubClient.fetchContent(anyString(), anyString())).willReturn(Map.of());

        List<KnowledgeParser.ParsedItem> parsedItems = parsedBehavioralItems();
        AtomicInteger index = new AtomicInteger();
        given(txHelper.upsertItem(eq("local-behavioral"), eq("data/behavioral-questions.json"), any()))
                .willAnswer(invocation -> {
                    int current = index.getAndIncrement();
                    if (current == 0) return KnowledgeSyncTransactionHelper.SyncAction.IMPORTED;
                    if (current == 1) return KnowledgeSyncTransactionHelper.SyncAction.UPDATED;
                    return KnowledgeSyncTransactionHelper.SyncAction.SKIPPED;
                });
        given(txHelper.deleteRemovedItems(eq("local-behavioral"), eq("data/behavioral-questions.json"), anyCollection()))
                .willReturn(2);

        KnowledgeSyncResult result = service.syncAll();

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(parsedItems.size() - 2);
        assertThat(result.failed()).isZero();
        assertThat(result.deleted()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> titlesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(txHelper).deleteRemovedItems(eq("local-behavioral"), eq("data/behavioral-questions.json"), titlesCaptor.capture());
        assertThat(titlesCaptor.getValue()).hasSize(parsedItems.size());
        assertThat(titlesCaptor.getValue()).contains(parsedItems.get(0).title(), parsedItems.get(1).title());
    }

    @Test
    void syncAll_incrementsFailedWhenParserThrowsForFetchedContent() {
        given(githubClient.fetchContent(anyString(), anyString())).willAnswer(invocation -> {
            String path = invocation.getArgument(1, String.class);
            if ("Computer Science/Computer Architecture".equals(path)) {
                return java.util.Collections.singletonMap("broken.md", null);
            }
            return Map.of();
        });
        given(txHelper.upsertItem(anyString(), anyString(), any())).willReturn(KnowledgeSyncTransactionHelper.SyncAction.SKIPPED);
        given(txHelper.deleteRemovedItems(anyString(), anyString(), anyCollection())).willReturn(0);

        KnowledgeSyncResult result = service.syncAll();

        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void syncAll_skipsUpsertWhenParserReturnsNoItemsForGithubContent() {
        given(githubClient.fetchContent(anyString(), anyString())).willAnswer(invocation -> {
            String path = invocation.getArgument(1, String.class);
            return Map.of(path + "/README.md", "");
        });
        given(txHelper.upsertItem(eq("local-behavioral"), eq("data/behavioral-questions.json"), any()))
                .willReturn(KnowledgeSyncTransactionHelper.SyncAction.SKIPPED);
        given(txHelper.deleteRemovedItems(anyString(), anyString(), anyCollection())).willReturn(0);

        service.syncAll();

        verify(txHelper, never()).upsertItem(eq("gyoogle-tech"), anyString(), any());
        verify(txHelper, never()).upsertItem(eq("jbee37142-beginner"), anyString(), any());
    }

    private List<KnowledgeParser.ParsedItem> parsedBehavioralItems() throws Exception {
        String raw = new ClassPathResource("data/behavioral-questions.json")
                .getContentAsString(StandardCharsets.UTF_8);
        KnowledgeSource.LocalFileSource source = new KnowledgeSource.LocalFileSource(
                "local-behavioral",
                "data/behavioral-questions.json",
                new JsonBehavioralParser()
        );
        return new JsonBehavioralParser().parse(raw, source);
    }
}

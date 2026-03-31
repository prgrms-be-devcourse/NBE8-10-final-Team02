package com.back.backend.domain.knowledge.source;

import com.back.backend.domain.knowledge.parser.KnowledgeParser;

import java.util.List;

public sealed interface KnowledgeSource
        permits KnowledgeSource.GithubSource, KnowledgeSource.LocalFileSource {

    String key();
    KnowledgeParser parser();

    /**
     * GitHub 레포에서 파일/디렉토리를 가져오는 소스.
     * paths에 파일 경로 또는 디렉토리 경로를 지정한다.
     * 디렉토리 경로 지정 시 하위 .md 파일 전체를 수집한다.
     */
    record GithubSource(
            String key,
            String repo,
            List<String> paths,
            KnowledgeParser parser
    ) implements KnowledgeSource {}

    /**
     * classpath 리소스 파일을 읽는 소스 (인성 질문 등 로컬 관리 데이터용).
     */
    record LocalFileSource(
            String key,
            String classpathPath,
            KnowledgeParser parser
    ) implements KnowledgeSource {}
}

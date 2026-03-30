package com.back.backend.domain.knowledge.source;

import com.back.backend.domain.knowledge.parser.JsonBehavioralParser;
import com.back.backend.domain.knowledge.parser.MarkdownHeadingParser;

import java.util.List;

public class KnowledgeSourceRegistry {

    public static final List<KnowledgeSource> SOURCES = List.of(

            // ── GitHub 소스 ──────────────────────────────────────────────────
            new KnowledgeSource.GithubSource(
                    "gyoogle-tech",
                    "gyoogle/tech-interview-for-developer",
                    List.of(
                            "Computer Science/Computer Architecture",
                            "Computer Science/Data Structure",
                            "Computer Science/Database",
                            "Computer Science/Network",
                            "Computer Science/Operating System",
                            "Computer Science/Software Engineering",
                            "Algorithm",
                            "Design Pattern",
                            "Language/Java",
                            "Language/Python",
                            "Web",
                            "Interview/Interview List.md",
                            "Interview/[Java] Interview List.md"
                    ),
                    new MarkdownHeadingParser()
            ),

            new KnowledgeSource.GithubSource(
                    "jbee37142-beginner",
                    "jbee37142/Interview_Question_for_Beginner",
                    List.of(
                            "Algorithm/README.md",
                            "DataStructure/README.md",
                            "Database/README.md",
                            "Java/README.md",
                            "Network/README.md",
                            "OS/README.md",
                            "DesignPattern/README.md",
                            "Development_common_sense/README.md"
                    ),
                    new MarkdownHeadingParser()
            ),

            // ── 로컬 파일 소스 ────────────────────────────────────────────────
            new KnowledgeSource.LocalFileSource(
                    "local-behavioral",
                    "data/behavioral-questions.json",
                    new JsonBehavioralParser()
            )
            // 새 GitHub 소스: GithubSource 패턴으로 항목 1개 + 필요 시 새 파서 클래스
            // 새 로컬 파일: LocalFileSource 패턴으로 항목 1개 + data/ 경로에 파일 추가
    );

    private KnowledgeSourceRegistry() {}
}

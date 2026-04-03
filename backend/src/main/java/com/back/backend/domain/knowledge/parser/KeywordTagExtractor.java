package com.back.backend.domain.knowledge.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 제목+내용 키워드 매칭으로 태그를 자동 추출한다.
 * 대소문자 무시. 키워드 하나라도 포함되면 해당 태그 부여.
 *
 * <p>!! 태그 목록(TAG_CATEGORY)이 knowledge_tags 테이블의 source of truth입니다.
 * 태그를 추가/수정할 때는 아래 두 파일도 함께 변경하세요:
 * <ul>
 *   <li>src/main/resources/data.sql (dev 환경 시드)</li>
 *   <li>src/main/resources/db/migration/V11__seed_knowledge_tags.sql (prod 환경 시드)</li>
 * </ul>
 */
public class KeywordTagExtractor {

    private static final Map<String, String> TAG_CATEGORY = new HashMap<>();
    private static final Map<String, List<String>> TAG_KEYWORDS = new HashMap<>();

    static {
        // ── domain 태그 ──────────────────────────────────────────────────────
        register("interview", "domain", List.of(
                "면접", "interview", "질문과 답변", "q:", "예상 질문", "기술 면접", "면접 질문"
        ));
        register("computer science", "domain", List.of(
                "cs", "computer science", "컴퓨터 과학", "컴퓨터공학", "기초 지식", "전공 지식"
        ));
        register("backend", "domain", List.of(
                "backend", "백엔드", "server-side", "서버 개발", "백엔드 개발"
        ));
        register("system-design", "domain", List.of(
                "시스템 설계", "system design", "아키텍처", "architecture", "확장성", "scalability",
                "분산 시스템", "distributed system", "마이크로서비스", "microservice", "msa", "고가용성", "high availability"
        ));

        // ── language 태그 ────────────────────────────────────────────────────
        register("java", "language", List.of(
                "java", "자바", "jvm", "jdk", "jre", "garbage collection", "가비지 컬렉션", "가비지컬렉션",
                "classloader", "클래스로더", "bytecode", "바이트코드", "autoboxing", "오토박싱",
                "generics", "제네릭", "reflection", "리플렉션"
        ));
        register("python", "language", List.of(
                "python", "파이썬", "파이썬 gil", " gil", "파이썬 인터프리터", "리스트 컴프리헨션"
        ));
        register("javascript", "language", List.of(
                "javascript", "자바스크립트", "node.js", "nodejs", "es6", "es2015",
                "이벤트루프", "event loop", "async", "await", "promise",
                "클로저", "closure", "호이스팅", "hoisting", "프로토타입", "prototype"
        ));
        register("kotlin", "language", List.of(
                "kotlin", "코틀린", "코루틴", "coroutine", "확장함수", "extension function", "data class"
        ));
        register("c", "language", List.of(
                "c언어", "c/c++", "포인터", "pointer", "메모리 누수", "memory leak", "스택 오버플로우"
        ));
        register("sql", "language", List.of(
                "sql", "select", "insert into", "group by", "having", "subquery", "서브쿼리", "집계함수", "aggregate"
        ));

        // ── topic 태그 ───────────────────────────────────────────────────────
        register("os", "topic", List.of(
                "운영체제", "operating system", "프로세스", "process", "스레드", "thread",
                "cpu 스케줄링", "cpu scheduling", "데드락", "deadlock", "교착상태",
                "컨텍스트 스위칭", "context switch", "context switching",
                "세마포어", "semaphore", "mutex", "뮤텍스",
                "가상 메모리", "virtual memory", "page fault", "캐싱",
                "커널", "kernel", "인터럽트", "interrupt", "파일시스템", "file system",
                "ipc", "프로세스 동기화", "임계구역", "critical section",
                "경쟁 조건", "race condition", "스와핑", "swapping",
                "단편화", "fragmentation", "페이지 교체", "page replacement"
        ));
        register("network", "topic", List.of(
                "네트워크", "network", "tcp", "udp", " ip ", "http", "https", "dns", "osi",
                "소켓", "socket", "rest", "restful", "cors", "쿠키", "cookie",
                "웹소켓", "websocket", "로드밸런서", "load balancer", "cdn", "라우팅", "routing",
                "3-way handshake", "4-way handshake", "흐름 제어", "flow control",
                "혼잡 제어", "congestion control", "서브넷", "subnet", "nat",
                "프로토콜", "protocol", "arp", "icmp", "tls handshake", "http/2", "http2"
        ));
        register("database", "topic", List.of(
                "데이터베이스", "database", " db ", "인덱스", "index", "트랜잭션", "transaction",
                "정규화", "normalization", "비정규화", "denormalization", "이상현상", "anomaly",
                "nosql", "redis", "mongodb", "mysql", "postgresql",
                "동시성", "concurrency", "쿼리 최적화", "query optimization",
                "acid", "원자성", "atomicity", "일관성", "consistency",
                "격리성", "isolation", "지속성", "durability",
                "격리수준", "isolation level", "dirty read", "phantom read",
                "b-tree", "b+tree", "저장프로시저", "stored procedure",
                "파티셔닝", "partitioning", "샤딩", "sharding", "replication", "복제"
        ));
        register("data-structure", "topic", List.of(
                "자료구조", "data structure", "배열", "array", "링크드리스트", "linked list",
                "스택", "stack", "큐", "queue", "이진트리", "binary tree",
                "이진탐색트리", "bst", "binary search tree",
                "그래프", "graph", "힙", "heap", "해시맵", "hashmap", "트라이", "trie",
                "덱", "deque", "우선순위큐", "priority queue",
                "avl", "레드블랙트리", "red-black tree"
        ));
        register("algorithm", "topic", List.of(
                "알고리즘", "algorithm", "버블정렬", "bubble sort", "삽입정렬", "insertion sort",
                "선택정렬", "selection sort", "퀵정렬", "quick sort",
                "병합정렬", "merge sort", "힙정렬", "heap sort",
                "dfs", "bfs", "너비 우선", "깊이 우선",
                "동적 프로그래밍", "dynamic programming", "그리디", "greedy",
                "분할정복", "divide and conquer",
                "시간복잡도", "time complexity", "공간복잡도", "space complexity",
                "빅오", "big-o", "재귀", "recursion",
                "위상정렬", "topological sort", "최단경로", "shortest path",
                "다익스트라", "dijkstra", "벨만포드", "bellman-ford",
                "플로이드", "floyd", "mst", "최소신장트리", "minimum spanning tree",
                "크루스칼", "kruskal", "프림", "prim",
                "이분탐색", "binary search", "투포인터", "two pointer"
        ));
        register("spring", "topic", List.of(
                "spring", "스프링", "spring boot", "ioc", " di ", "dependency injection", "의존성 주입",
                "aop", " mvc", "jpa", "hibernate", " bean", " 빈 ",
                "@service", "@repository", "@controller", "@restcontroller", "@autowired", "@transactional",
                "인터셉터", "interceptor", "dispatcherservlet",
                "영속성 컨텍스트", "persistence context",
                "지연로딩", "lazy loading", "즉시로딩", "eager loading", "n+1"
        ));
        register("design-pattern", "topic", List.of(
                "디자인 패턴", "design pattern",
                "singleton", "싱글톤", "factory", "팩토리", "abstract factory",
                "observer", "옵저버", "strategy", "전략 패턴",
                "decorator", "proxy", "프록시", "adapter", "어댑터",
                "facade", "퍼사드", "template method", "템플릿 메서드",
                "command", "커맨드", "builder", "빌더", "composite", "컴포지트"
        ));
        register("security", "topic", List.of(
                "보안", "security", "인증", "authentication", "인가", "authorization",
                "jwt", "oauth", "oauth2", "xss", "csrf", "sql injection",
                "암호화", "encryption", "tls", "공개키", "비밀키", "public key", "private key",
                "해시함수", "bcrypt", "salt", "세션 하이재킹", "session hijacking",
                "refresh token"
        ));
        register("git", "topic", List.of(
                " git", "버전 관리", "version control", "merge", "rebase", "branch",
                "cherry-pick", "stash", "git flow", "브랜치 전략"
        ));
        register("docker", "topic", List.of(
                "docker", "도커", "container", "컨테이너",
                "kubernetes", "k8s", "쿠버네티스", "dockerfile", "docker-compose",
                "오케스트레이션", "orchestration", " pod", "파드",
                "ci/cd", "무중단 배포", "blue-green", "롤링 배포"
        ));
        register("oop", "topic", List.of(
                "객체지향", "oop", "object-oriented",
                "캡슐화", "encapsulation", "상속", "inheritance",
                "다형성", "polymorphism", "추상화", "abstraction",
                "solid", "단일책임", "개방폐쇄", "리스코프", "인터페이스 분리", "의존성 역전",
                "추상클래스", "abstract class",
                "오버로딩", "overloading", "오버라이딩", "overriding"
        ));
        register("functional", "topic", List.of(
                "함수형", "functional", "람다", "lambda", "stream", "불변", "immutable",
                "순수함수", "pure function", "사이드 이펙트", "side effect",
                "고차함수", "higher-order function", "커링", "currying"
        ));
        register("testing", "topic", List.of(
                "단위테스트", "unit test", "통합테스트", "integration test",
                "tdd", "bdd", "테스트 주도", "mock", "목 객체", "stub", "스텁",
                "mockito", "junit", "테스트 커버리지", "coverage", "회귀 테스트", "regression"
        ));
        register("behavioral", "topic", List.of(
                "자기소개", "지원 동기", "강점", "약점", "성격", "인성",
                "협업", "갈등", "리더십", "팀워크", "teamwork",
                "커뮤니케이션", "실패 경험", "성공 경험", "어려웠던", "힘들었던",
                "1분 자기소개", "30초 자기소개", "본인 소개", "왜 지원", "장단점",
                "soft skill", "motivation", "personality"
        ));
    }

    private static void register(String tag, String category, List<String> keywords) {
        TAG_CATEGORY.put(tag, category);
        TAG_KEYWORDS.put(tag, keywords);
    }

    public static List<String> extract(String text) {
        String lower = text.toLowerCase();
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : TAG_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
        return result;
    }

    public static String categoryOf(String tag) {
        return TAG_CATEGORY.getOrDefault(tag, "topic");
    }

//    public static Map<String, String> allTags() {
//        return Map.copyOf(TAG_CATEGORY);
//    }

    private KeywordTagExtractor() {}
}

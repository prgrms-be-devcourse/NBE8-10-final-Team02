package com.back.backend.domain.github.analysis;

import com.back.backend.domain.github.entity.NodeType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JavaParser 기반 Java 소스 파일 정적 분석기.
 *
 * 분석 결과: 클래스별 FQN, 메서드 목록, 참조 타입 목록 (Call Graph 엣지용).
 *
 * 한계:
 *   - 타입 추론 없이 이름 기반 참조만 추출한다 (컴파일 없이 AST만 사용).
 *   - 오버로드 메서드의 파라미터 타입 FQN은 단순 이름으로 기록된다.
 *   - 정확도보다 처리 속도와 설치 단순성을 우선한다.
 */
@Component
public class JavaStaticAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaStaticAnalyzer.class);

    /**
     * 단일 .java 파일을 분석하여 AnalysisNode 목록을 반환한다.
     * 파싱 실패 시 빈 리스트를 반환한다 (전체 파이프라인은 계속 진행).
     *
     * @param javaFile repo 루트 기준 절대 경로
     * @param repoRoot repo 루트 Path (상대 경로 계산용)
     */
    // ParserConfiguration은 불변이므로 공유해도 안전
    private static final ParserConfiguration PARSER_CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

    public List<AnalysisNode> analyze(Path javaFile, Path repoRoot) {
        String relativePath = repoRoot.relativize(javaFile).toString().replace('\\', '/');

        try {
            // 스레드마다 JavaParser 인스턴스를 생성 — StaticJavaParser(static 상태)는 사용하지 않는다
            JavaParser parser = new JavaParser(PARSER_CONFIG);
            ParseResult<CompilationUnit> result = parser.parse(javaFile);
            CompilationUnit cu = result.getResult()
                    .orElseThrow(() -> new IllegalStateException("Parse result is empty"));
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            List<AnalysisNode> nodes = new ArrayList<>();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String simpleName = classDecl.getNameAsString();
                String fqn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

                // 클래스 위치
                int locStart = classDecl.getBegin().map(p -> p.line).orElse(0);
                int locEnd = classDecl.getEnd().map(p -> p.line).orElse(0);

                // 메서드 목록
                List<AnalysisNode.MethodInfo> methods = extractMethods(classDecl);

                // 참조 타입 (Call Graph 엣지 근사치)
                Set<String> referencedTypes = extractReferencedTypes(cu, classDecl);

                // import에서 FQN을 알 수 있는 타입만 포함
                Set<String> calls = resolveCallsFromImports(cu, referencedTypes);

                nodes.add(new AnalysisNode(
                        fqn,
                        relativePath,
                        locStart,
                        locEnd,
                        NodeType.CLASS,
                        new ArrayList<>(calls),
                        methods
                ));
            });

            return nodes;

        } catch (IOException e) {
            log.warn("Failed to read java file: {}, reason: {}", relativePath, e.getMessage());
            return List.of();
        } catch (Exception e) {
            // 파싱 실패 (문법 오류 등) → 해당 파일 건너뜀
            log.debug("Failed to parse java file: {}, reason: {}", relativePath, e.getMessage());
            return List.of();
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 추출 유틸
    // ─────────────────────────────────────────────────

    private List<AnalysisNode.MethodInfo> extractMethods(ClassOrInterfaceDeclaration classDecl) {
        List<AnalysisNode.MethodInfo> methods = new ArrayList<>();

        classDecl.getMethods().forEach(method -> {
            String name = method.getNameAsString();
            String signature = buildSignature(method);
            int start = method.getBegin().map(p -> p.line).orElse(0);
            int end = method.getEnd().map(p -> p.line).orElse(0);
            methods.add(new AnalysisNode.MethodInfo(name, signature, start, end));
        });

        return methods;
    }

    private String buildSignature(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder(method.getNameAsString()).append('(');
        method.getParameters().forEach(param -> {
            if (sb.charAt(sb.length() - 1) != '(') sb.append(',');
            sb.append(param.getType().asString());
        });
        sb.append("):").append(method.getType().asString());
        return sb.toString();
    }

    /**
     * 클래스 내에서 참조되는 타입 이름(단순 이름)을 추출한다.
     * 필드 타입, 메서드 반환 타입, 파라미터 타입, new 생성자, 메서드 호출 스코프 타입 포함.
     */
    private Set<String> extractReferencedTypes(CompilationUnit cu,
                                                ClassOrInterfaceDeclaration classDecl) {
        Set<String> types = new LinkedHashSet<>();

        // 필드/메서드 파라미터/반환 타입
        classDecl.findAll(ClassOrInterfaceType.class).forEach(t ->
                types.add(t.getNameAsString()));

        // new ClassName() 생성
        classDecl.findAll(ObjectCreationExpr.class).forEach(expr ->
                types.add(expr.getType().getNameAsString()));

        // 정적 메서드 호출 스코프 (예: SomeClass.doSomething())
        classDecl.findAll(MethodCallExpr.class).forEach(call ->
                call.getScope().ifPresent(scope -> {
                    if (scope instanceof FieldAccessExpr fae) {
                        types.add(fae.getNameAsString());
                    } else {
                        String scopeStr = scope.toString();
                        // 단순 대문자로 시작하는 이름만 (클래스 참조 가능성)
                        if (!scopeStr.isEmpty() && Character.isUpperCase(scopeStr.charAt(0))) {
                            types.add(scopeStr);
                        }
                    }
                }));

        return types;
    }

    /**
     * import 목록에서 단순 이름 → FQN 매핑을 만들어 calls Set을 완성한다.
     * import에 없는 타입은 같은 패키지이거나 java.lang이므로 제외한다.
     */
    private Set<String> resolveCallsFromImports(CompilationUnit cu, Set<String> simpleNames) {
        Set<String> resolved = new LinkedHashSet<>();

        cu.getImports().forEach(importDecl -> {
            if (importDecl.isAsterisk()) return; // 와일드카드 import는 무시
            String importFqn = importDecl.getNameAsString();
            String simpleName = importFqn.contains(".")
                    ? importFqn.substring(importFqn.lastIndexOf('.') + 1)
                    : importFqn;
            if (simpleNames.contains(simpleName)) {
                resolved.add(importFqn);
            }
        });

        return resolved;
    }

    /**
     * repo 내 모든 .java 파일을 분석하여 AnalysisNode 목록을 반환한다.
     *
     * @param targetFiles null이면 전체 분석, non-null이면 해당 파일(상대 경로)만 분석
     */
    public List<AnalysisNode> analyzeAll(Path repoRoot, Optional<Set<String>> targetFiles) {
        List<Path> javaFiles;
        try (var stream = java.nio.file.Files.walk(repoRoot)) {
            javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> targetFiles.map(files -> {
                        String rel = repoRoot.relativize(p).toString().replace('\\', '/');
                        return files.contains(rel);
                    }).orElse(true))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to walk repo directory: {}, reason: {}", repoRoot, e.getMessage());
            return List.of();
        }

        // 파일 목록을 먼저 수집한 뒤 병렬 파싱
        // 각 스레드가 독립적인 JavaParser 인스턴스를 사용하므로 thread-safe
        return javaFiles.parallelStream()
                .flatMap(javaFile -> analyze(javaFile, repoRoot).stream())
                .toList();
    }
}

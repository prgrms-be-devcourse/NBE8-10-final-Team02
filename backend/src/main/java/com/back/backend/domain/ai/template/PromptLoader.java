package com.back.backend.domain.ai.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * classpath에서 프롬프트 .txt 파일을 로딩
 * 최초 1회만 로딩하고 ConcurrentHashMap으로 캐싱 — 런타임에 변경되지 않음
 */
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private static final String BASE_PATH = "ai/templates/";

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 프롬프트 파일을 로딩하여 문자열로 반환
     * 캐시에 있으면 캐시에서 반환, 없으면 classpath에서 로딩 후 캐싱
     *
     * @param filePath 템플릿 기준 상대 경로 (예: "system/common-system.txt")
     * @return 프롬프트 텍스트
     * @throws IllegalArgumentException 파일이 존재하지 않을 경우
     */
    public String load(String filePath) {
        return cache.computeIfAbsent(filePath, this::loadFromClasspath);
    }

    /**
     * base 프롬프트와 role overlay를 합성하여 반환
     * overlay 파일이 null이거나 존재하지 않으면 base만 반환
     *
     * @param baseFile    기본 프롬프트 파일 경로 (예: "developer/ai.interview.evaluate.v1.txt")
     * @param overlayFile overlay 프롬프트 파일 경로 (예: "developer/evaluate-role/backend.txt"), null 허용
     * @return 합성된 프롬프트 텍스트
     */
    public String loadComposite(String baseFile, String overlayFile) {
        String base = load(baseFile);
        if (overlayFile == null) {
            return base;
        }

        String fullPath = BASE_PATH + overlayFile;
        ClassPathResource resource = new ClassPathResource(fullPath);
        if (!resource.exists()) {
            log.warn("직무별 overlay 파일이 없어 base 프롬프트만 사용: {}", fullPath);
            return base;
        }

        String overlay = load(overlayFile);
        return base + "\n\n" + overlay;
    }

    /**
     * classpath에서 파일을 읽어 문자열로 반환
     * ConcurrentHashMap.computeIfAbsent()의 매핑 함수로 사용되며, 키당 1회만 실행
     */
    private String loadFromClasspath(String filePath) {
        String fullPath = BASE_PATH + filePath;
        ClassPathResource resource = new ClassPathResource(fullPath);

        if (!resource.exists()) {
            throw new IllegalArgumentException("프롬프트 파일을 찾을 수 없습니다: " + fullPath);
        }

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            String content = FileCopyUtils.copyToString(reader);
            log.debug("프롬프트 파일 로딩 완료: {}", fullPath);
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("프롬프트 파일 로딩 중 오류: " + fullPath, e);
        }
    }
}

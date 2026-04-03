# syntax=docker/dockerfile:1.7

# =========================
# 1️⃣ Build Stage
# =========================
# Java 25 환경 (GraalVM 기준, 필요시 eclipse-temurin:25-jdk 등으로 변경 가능)
FROM container-registry.oracle.com/graalvm/jdk:25 AS builder
WORKDIR /app

# 의존성 캐싱을 위한 Gradle 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# 의존성 다운로드 (소스 코드가 변경되어도 이 단계는 캐시가 유지되어 빌드가 훨씬 빨라집니다)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 실제 소스 코드 복사
COPY src src

# 애플리케이션 빌드 (CI 파이프라인에서 이미 테스트를 거쳤다고 가정하고 -x test로 빌드 속도 극대화)
RUN ./gradlew build -x test --no-daemon

# =========================
# 2️⃣ Layer Extract Stage
# =========================
FROM container-registry.oracle.com/graalvm/jdk:25 AS extractor
WORKDIR /app

# 빌드된 jar 파일 복사 및 Spring Boot 4.0 호환 레이어 추출
COPY --from=builder /app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# =========================
# 3️⃣ Runtime Stage
# =========================
FROM container-registry.oracle.com/graalvm/jdk:25
WORKDIR /app

# git: RepoCloneService의 git clone/fetch 실행에 필요
# python3 + pip: Kotlin/Go/Rust/C/C++ 정적 분석 스크립트 (tree-sitter 기반)
# nodejs: TypeScript/JavaScript 분석 스크립트 (ts-morph)
# gitleaks: 시크릿 스캔 (GitleaksService) — OCI ARM64(aarch64) 환경용 linux_arm64 바이너리
ARG GITLEAKS_VERSION=8.18.4
RUN microdnf install -y git python3 python3-pip nodejs tar curl && microdnf clean all && \
    pip3 install --no-cache-dir \
      tree-sitter tree-sitter-kotlin tree-sitter-go \
      tree-sitter-rust tree-sitter-c tree-sitter-cpp && \
    curl -sSfL \
      "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_arm64.tar.gz" \
      | tar -xz -C /usr/local/bin gitleaks && \
    chmod +x /usr/local/bin/gitleaks

# 한글 인코딩 및 한국 시간대(KST) 설정
ENV LANG=ko_KR.UTF-8 \
    LC_ALL=ko_KR.UTF-8 \
    TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

# Spring Boot 레이어 복사 — 변경 빈도 오름차순으로 배치해 캐시 효율 극대화
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./

# 분석 스크립트 복사 및 Node.js 의존성 설치 (package-lock.json 기반 재현 가능한 빌드)
# application 레이어 앞에 배치: 스크립트 변경이 앱 코드 캐시에 영향을 주지 않음
COPY analysis-scripts /opt/analysis-scripts
RUN cd /opt/analysis-scripts && npm ci --omit=dev

COPY --from=extractor /app/application/ ./

EXPOSE 8080

# Java 21 이상에서 효율이 좋은 ZGC 사용 및 메모리 최적화 옵션 적용
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75", \
  "-XX:InitialRAMPercentage=50", \
  "-Dspring.profiles.active=prod", \
  "org.springframework.boot.loader.launch.JarLauncher"]

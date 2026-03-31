plugins {
  java
  id("org.springframework.boot") version "4.0.3"
  id("io.spring.dependency-management") version "1.1.7"
}

group = "com.back"
version = "0.0.1-SNAPSHOT"
description = "backend"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }
}

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-flyway")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")
  implementation("org.springframework.boot:spring-boot-h2console")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-json")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-security")

  // Social Login
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  // JWT
  implementation("io.jsonwebtoken:jjwt-api:0.13.0")
  runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
  runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

  // Redis
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  compileOnly("org.projectlombok:lombok")
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  runtimeOnly("com.h2database:h2")
  annotationProcessor("org.projectlombok:lombok")
  runtimeOnly("org.postgresql:postgresql")

  testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:postgresql:1.20.6")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
  testImplementation("org.wiremock:wiremock-standalone:3.12.1")

  // API Docs
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0")

  // Monitoring
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("io.micrometer:micrometer-registry-prometheus")

  // JSON Schema
  implementation("com.networknt:json-schema-validator:1.5.6")

  // Java static analysis (AST parsing for code index)
  implementation("com.github.javaparser:javaparser-core:3.26.3")

  // 문서 텍스트 추출: PDF(PDFBox), DOCX(Apache POI)
  implementation("org.apache.pdfbox:pdfbox:3.0.3")
  implementation("org.apache.poi:poi-ooxml:5.3.0")
}

tasks.withType<Test> {
  useJUnitPlatform()
}

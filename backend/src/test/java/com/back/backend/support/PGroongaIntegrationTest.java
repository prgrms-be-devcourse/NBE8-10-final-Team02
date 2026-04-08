package com.back.backend.support;

import com.back.backend.config.PGroongaTestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PGroonga 전문 검색 통합 테스트용 메타 어노테이션.
 *
 * <p>SpringBootTest + test 프로파일 + PGroonga 설치 PostgreSQL Testcontainer를 한 번에 적용한다.
 * 일반 통합 테스트({@link IntegrationTest})와 달리 {@code groonga/pgroonga} 이미지를 사용하므로
 * {@code &@~} 연산자 기반 FTS가 실제로 동작한다.</p>
 *
 * <ul>
 *   <li><b>로컬에서 Docker Desktop이 실행 중이어야 한다.</b>
 *       처음 실행 시 PGroonga 이미지 pull 시간이 걸릴 수 있다.</li>
 *   <li><b>일반 {@code @IntegrationTest}와 컨텍스트를 공유하지 않는다.</b>
 *       PostgreSQL 이미지가 다르므로 별도 Spring 컨텍스트가 뜬다.
 *       PGroonga 테스트 클래스끼리는 컨텍스트를 재사용한다.</li>
 *   <li><b>순수 단위 테스트에는 사용하지 말 것.</b>
 *       {@code @ExtendWith(MockitoExtension.class)}로 충분한 경우 이 어노테이션이 불필요하다.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(PGroongaTestcontainersConfiguration.class)
public @interface PGroongaIntegrationTest {
}

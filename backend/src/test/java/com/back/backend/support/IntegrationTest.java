package com.back.backend.support;

import com.back.backend.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Repository / Schema 통합 테스트용 메타 어노테이션.
 *
 * <p>SpringBootTest + test 프로파일 + Testcontainers PostgreSQL을 한 번에 적용합니다.
 * API 테스트(MockMvc 필요)는 {@link ApiTestBase}를 상속하세요.
 *
 * <pre>{@code
 * @IntegrationTest
 * @Transactional   // 테스트 후 DB 롤백
 * class UserRepositoryTest {
 *     @Autowired UserRepository userRepository;
 * }
 * }</pre>
 *
 * <p><b>주의:</b>
 * <ul>
 *   <li><b>로컬에서 Docker Desktop이 실행 중이어야 합니다.</b>
 *       Testcontainers가 PostgreSQL 컨테이너를 띄우기 때문입니다.
 *       꺼진 상태로 테스트를 실행하면 {@code IllegalStateException}이 발생합니다.</li>
 *
 *   <li><b>컨텍스트 캐시 사용:</b>
 *       같은 설정의 테스트끼리 Spring 컨텍스트를 공유합니다(속도 이점).
 *       테스트 클래스마다 {@code @ActiveProfiles}나 {@code @Import}를 다르게 지정하면
 *       컨텍스트가 새로 뜨므로 전체 테스트 시간이 늘어납니다.</li>
 *
 *   <li><b>단위 테스트(Unit Test)에는 사용하지 마세요.</b>
 *       Spring 컨텍스트가 필요 없는 순수 로직 검증은
 *       {@code @ExtendWith(MockitoExtension.class)}만으로 충분합니다.</li>
 * </ul>
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}

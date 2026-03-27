package com.back.backend.support;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * API(HTTP 계층) 테스트 베이스 클래스.
 *
 * <p>{@link IntegrationTest} 설정을 포함하므로 별도로 붙일 필요 없습니다.
 * {@code @AutoConfigureMockMvc}로 MockMvc + Spring Security가 자동 구성됩니다.
 *
 * <h2>어느 테스트에 써야 하나?</h2>
 * <p>HTTP 계층(인증, 응답 형식, 상태 코드)을 검증하는 테스트.
 * 비즈니스 로직은 {@code @MockitoBean}으로 대체하고 HTTP 레이어에만 집중합니다.
 *
 * <h2>Mock 위주 (HTTP 레이어만 검증)</h2>
 * <pre>{@code
 * class UserApiTest extends ApiTestBase {
 *
 *     // Service는 Mock으로 대체 — DB 없이 HTTP 레이어만 검증
 *     @MockitoBean
 *     private UserService userService;
 *
 *     @Test
 *     void getMe_returns401WhenUnauthenticated() throws Exception {
 *         mockMvc.perform(get("/api/v1/users/me"))
 *                .andExpect(status().isUnauthorized());
 *     }
 * }
 * }</pre>
 *
 * <h2>TestFixtures + WireMock (실제 DB + 외부 API stub)</h2>
 * <pre>{@code
 * @Transactional  // 테스트 종료 시 자동 롤백
 * class GithubApiTest extends ApiTestBase {
 *
 *     @Autowired TestFixtures fixtures;
 *     private User user;
 *     private GithubRepository repo;
 *
 *     @BeforeEach
 *     void setUp() {
 *         user = fixtures.createUser("test@test.com", "Test User");
 *         GithubConnection conn = fixtures.createConnection(user);
 *         repo = fixtures.createRepo(conn, "my-project", true);
 *         fixtures.createUserCommit(repo, "feat: add feature");
 *     }
 *
 *     @Test
 *     void getRepositories_returnsOk() throws Exception {
 *         // 외부 GitHub API 호출이 있다면 WireMock으로 stub
 *         wireMock.stubFor(get(urlPathMatching("/user/repos"))
 *                 .willReturn(aResponse().withStatus(200)
 *                         .withHeader("Content-Type", "application/json")
 *                         .withBodyFile("github/repos.json")));  // src/test/resources/__files/
 *
 *         mockMvc.perform(get("/api/v1/github/repositories")
 *                         .header("Authorization", "Bearer " + jwtFor(user)))
 *                 .andExpect(status().isOk());
 *     }
 * }
 * }</pre>
 *
 * <p><b>주의:</b>
 * <ul>
 *   <li><b>{@code @MockitoBean}은 컨텍스트 캐시를 분리합니다.</b>
 *       {@code @MockitoBean}이 선언된 테스트는 mock이 없는 통합 테스트와
 *       별도의 Spring 컨텍스트를 사용합니다. 이는 정상 동작이며,
 *       {@code ApiTestBase}를 상속하는 테스트들끼리는 컨텍스트를 공유합니다.</li>
 *
 *   <li><b>추가 {@code @Import}는 컨텍스트를 다시 분리합니다.</b>
 *       테스트용 가짜 컨트롤러 등 특수한 빈이 필요하다면
 *       해당 테스트 클래스에 직접 {@code @Import}를 추가하면 됩니다.
 *       단, 그 테스트는 독자적인 컨텍스트를 사용하게 됩니다.</li>
 *
 *   <li><b>Service를 실제로 실행하고 싶다면 {@code @MockitoBean} 없이 사용하세요.</b>
 *       이 경우 DB 접근이 발생하므로 Testcontainers가 필요합니다 (이미 포함됨).</li>
 *
 *   <li><b>Spring Security는 자동 적용됩니다.</b>
 *       인증이 필요한 엔드포인트 테스트 시 {@code @WithMockUser}를 사용하고,
 *       인증 없는 요청의 401 응답도 반드시 테스트하세요.</li>
 * </ul>
 */
@IntegrationTest
public abstract class ApiTestBase {

    // 모든 하위 테스트가 동일한 Clock mock을 공유하여 Spring 컨텍스트 캐시를 최대화한다.
    // 시각 고정이 필요한 테스트는 @BeforeEach에서 given(clock.instant()).willReturn(...)으로 설정한다.
    @MockitoBean
    protected Clock clock;

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mockMvc;

    // SecurityConfig에 STATELESS 세션이 설정돼 있어 @AutoConfigureMockMvc만으로는
    // @WithMockUser가 동작하지 않음. springSecurity()를 명시적으로 적용해야
    // SecurityContextHolderFilter가 테스트용 인증 컨텍스트를 덮어쓰지 않음.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // GitHub API와 AI API(Gemini) 호출을 가로채는 WireMock 서버.
    // static: 컨텍스트 로드 전에 포트가 확정되어 @DynamicPropertySource에서 사용 가능.
    @RegisterExtension
    protected static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    // WireMock 포트를 Spring 프로퍼티로 주입 — GithubApiClient, GeminiClient가 이 URL을 사용함.
    @DynamicPropertySource
    static void overrideExternalApiUrls(DynamicPropertyRegistry registry) {
        registry.add("github.api.base-url", wireMock::baseUrl);
        registry.add("ai.gemini.base-url", wireMock::baseUrl);
    }
}

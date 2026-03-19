package com.back.backend.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

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
 * <h2>사용 예시</h2>
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
 *                .andExpect(status().isUnauthorized())
 *                .andExpect(jsonPath("$.success").value(false))
 *                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
 *     }
 *
 *     @Test
 *     @WithMockUser  // 인증된 사용자 시뮬레이션
 *     void getMe_returns200WhenAuthenticated() throws Exception {
 *         given(userService.getMe(any())).willReturn(new UserResponse(...));
 *
 *         mockMvc.perform(get("/api/v1/users/me"))
 *                .andExpect(status().isOk())
 *                .andExpect(jsonPath("$.success").value(true))
 *                .andExpect(jsonPath("$.data.email").value("test@test.com"));
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
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mockMvc;
}

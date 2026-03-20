package com.back.backend.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 예시용으로 만든 클래스입니다. 사용할 필요는 없습니다.
 */

/**
 * API(Controller) 계층의 슬라이스 테스트를 위한 베이스 클래스입니다.
 * 모든 컨트롤러 테스트에서 공통으로 사용하는 MockMvc와 프로파일 설정을 관리합니다.
 */
@ActiveProfiles("test")
public abstract class WebMvcTestBase {

    /**
     * 2. MockMvc: 서블릿 컨테이너를 실제로 띄우지 않고도
     * HTTP 요청(GET, POST 등)을 시뮬레이션하고 응답을 검증할 수 있게 해주는 핵심 객체입니다.
     * 에러 표시가 나도 무시.
     */

    @Autowired
    protected MockMvc mockMvc;

}

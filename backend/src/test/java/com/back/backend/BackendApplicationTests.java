package com.back.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}

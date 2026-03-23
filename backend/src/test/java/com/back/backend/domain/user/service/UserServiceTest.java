package com.back.backend.domain.user.service;

import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.exception.UserNotFoundException;
import com.back.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getMyProfile_returnsResponse() {
        User user = User.builder()
                .email("tester@example.com")
                .displayName("tester")
                .profileImageUrl("https://example.com/profile.png")
                .status(UserStatus.ACTIVE)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThat(userService.getMyProfile(1L))
                .extracting("displayName", "email", "profileImageUrl", "status")
                .containsExactly("tester", "tester@example.com", "https://example.com/profile.png", "active");
    }

    @Test
    void getMyProfile_throwsWhenUserMissing() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(1L))
                .isInstanceOf(UserNotFoundException.class);
    }
}

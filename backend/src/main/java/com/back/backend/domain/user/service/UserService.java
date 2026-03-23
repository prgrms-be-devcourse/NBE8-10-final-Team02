package com.back.backend.domain.user.service;

import com.back.backend.domain.user.dto.response.UserProfileResponse;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.exception.UserNotFoundException;
import com.back.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getMyProfile(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return UserProfileResponse.from(user);
    }
}

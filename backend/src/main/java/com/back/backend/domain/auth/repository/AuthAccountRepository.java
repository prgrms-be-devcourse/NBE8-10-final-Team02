package com.back.backend.domain.auth.repository;

import com.back.backend.domain.auth.entity.AuthAccount;
import com.back.backend.domain.auth.entity.AuthProvider;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.auth.entity.AuthAccount;
import com.back.backend.domain.auth.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    Optional<AuthAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    // 사용자에 연결된 모든 소셜 계정 조회 (연결된 provider 목록 확인용)
    List<AuthAccount> findByUser(User user);
}

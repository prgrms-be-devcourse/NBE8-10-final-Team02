package com.back.backend.global.security.auth.repository;

import com.back.backend.global.security.auth.entity.AuthAccount;
import com.back.backend.global.security.auth.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    Optional<AuthAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}

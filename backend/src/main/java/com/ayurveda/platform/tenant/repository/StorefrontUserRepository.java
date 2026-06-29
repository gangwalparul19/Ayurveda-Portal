package com.ayurveda.platform.tenant.repository;

import com.ayurveda.platform.tenant.entity.StorefrontUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorefrontUserRepository extends JpaRepository<StorefrontUser, Long> {

    Optional<StorefrontUser> findByEmail(String email);

    Optional<StorefrontUser> findByPhone(String phone);

    boolean existsByEmail(String email);
}

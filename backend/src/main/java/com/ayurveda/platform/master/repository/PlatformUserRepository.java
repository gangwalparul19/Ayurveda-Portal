package com.ayurveda.platform.master.repository;

import com.ayurveda.platform.master.entity.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

    Optional<PlatformUser> findByUsername(String username);

    Optional<PlatformUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<PlatformUser> findAllByTenantId(Long tenantId);

    List<PlatformUser> findAllByTenantTenantKey(String tenantKey);

    List<PlatformUser> findAllByRole(PlatformUser.UserRole role);
}

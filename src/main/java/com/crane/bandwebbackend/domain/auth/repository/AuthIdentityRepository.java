package com.crane.bandwebbackend.domain.auth.repository;

import com.crane.bandwebbackend.domain.auth.entity.AuthIdentity;
import com.crane.bandwebbackend.domain.auth.entity.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    @Query("select i from AuthIdentity i join fetch i.user where i.provider = :provider and i.providerUid = :uid")
    Optional<AuthIdentity> findWithUser(@Param("provider") AuthProvider provider, @Param("uid") String uid);

    Optional<AuthIdentity> findByUserIdAndProvider(Long userId, AuthProvider provider);
}

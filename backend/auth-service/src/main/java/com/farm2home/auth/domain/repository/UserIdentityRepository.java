package com.farm2home.auth.domain.repository;

import com.farm2home.auth.domain.entity.UserIdentity;
import com.farm2home.auth.domain.enums.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    /** The whole of returning-Google-user login: (provider, providerUserId) is unique, and the
     *  provider's own subject id never changes, so this is a stable, direct lookup - no email
     *  matching involved once a user has signed in with Google at least once before. */
    Optional<UserIdentity> findByProviderAndProviderUserId(IdentityProvider provider, String providerUserId);

    boolean existsByUserIdAndProvider(UUID userId, IdentityProvider provider);
}

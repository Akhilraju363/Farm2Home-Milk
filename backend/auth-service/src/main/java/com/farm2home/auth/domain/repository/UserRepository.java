package com.farm2home.auth.domain.repository;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.common.core.constants.RegexConstants;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByMobileAndDeletedFalse(String mobile);

    Optional<User> findByEmailAndDeletedFalse(String email);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    boolean existsByMobileAndDeletedFalse(String mobile);

    boolean existsByEmailAndDeletedFalse(String email);

    /** Login accepts the 10-digit mobile number, the registered email address, or the account's
     *  username as the identifier (see LoginRequest) - this is the single place that decides
     *  which one a given string is, so UserDetailsServiceImpl (authentication) and
     *  AuthServiceImpl (post-auth lookup) can't drift out of sync on the rule. Order matters:
     *  mobile format is checked first (most common case), then a simple "contains @" heuristic
     *  for email, and username is the catch-all - real usernames are auto-generated as
     *  firstname.lastname (see UserMapper), so this only actually matches for identifiers that
     *  were deliberately set to a bare username, e.g. staff/admin accounts. */
    default Optional<User> findByIdentifierAndDeletedFalse(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        if (identifier.matches(RegexConstants.MOBILE_PATTERN)) {
            return findByMobileAndDeletedFalse(identifier);
        }
        if (identifier.contains("@")) {
            return findByEmailAndDeletedFalse(identifier);
        }
        return findByUsernameAndDeletedFalse(identifier);
    }
}

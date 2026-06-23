package com.farm2home.auth.domain.repository;

import com.farm2home.auth.domain.entity.User;
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
}

package com.farm2home.auth.domain.repository;

import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndDeletedFalse(RoleType name);
}

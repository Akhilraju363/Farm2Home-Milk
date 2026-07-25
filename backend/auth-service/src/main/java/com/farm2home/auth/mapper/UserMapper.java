package com.farm2home.auth.mapper;

import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.auth.dto.request.RegisterRequest;
import com.farm2home.auth.dto.response.AuthResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "rolesToStringList")
    @Mapping(target = "verified", source = "verified")
    AuthResponse.UserInfo toUserInfo(User user);

    // username, passwordHash, active, verified and roles aren't sourced from the request -
    // they're computed/assigned by AuthServiceImpl after this runs (username is derived from
    // name parts, passwordHash needs the PasswordEncoder, roles come from a repository lookup).
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "verified", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    User toEntity(RegisterRequest request);

    @Named("rolesToStringList")
    default List<String> rolesToStringList(Set<Role> roles) {
        if (roles == null) return List.of();
        return roles.stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toList());
    }
}

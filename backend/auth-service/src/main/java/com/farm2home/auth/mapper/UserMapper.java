package com.farm2home.auth.mapper;

import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
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

    @Named("rolesToStringList")
    default List<String> rolesToStringList(Set<Role> roles) {
        if (roles == null) return List.of();
        return roles.stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toList());
    }
}

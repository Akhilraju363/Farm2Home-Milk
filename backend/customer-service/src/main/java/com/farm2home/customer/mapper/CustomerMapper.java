package com.farm2home.customer.mapper;

import com.farm2home.customer.domain.entity.Customer;
import com.farm2home.customer.dto.request.UpdateCustomerRequest;
import com.farm2home.customer.dto.response.CustomerResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Condition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "status", expression = "java(customer.getStatus().name())")
    CustomerResponse toResponse(Customer customer);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerCode", ignore = true)
    @Mapping(target = "mobile", ignore = true)
    @Mapping(target = "profileImageUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromRequest(UpdateCustomerRequest request, @MappingTarget Customer customer);

    @Condition
    default boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}

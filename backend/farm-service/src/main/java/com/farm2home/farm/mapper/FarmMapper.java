package com.farm2home.farm.mapper;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.entity.Farm;
import com.farm2home.farm.domain.entity.HealthRecord;
import com.farm2home.farm.domain.entity.Vaccination;
import com.farm2home.farm.dto.request.CreateCowRequest;
import com.farm2home.farm.dto.request.CreateFarmRequest;
import com.farm2home.farm.dto.request.CreateHealthRecordRequest;
import com.farm2home.farm.dto.request.CreateVaccinationRequest;
import com.farm2home.farm.dto.request.UpdateCowRequest;
import com.farm2home.farm.dto.request.UpdateFarmRequest;
import com.farm2home.farm.dto.request.UpdateVaccinationRequest;
import com.farm2home.farm.dto.response.CowResponse;
import com.farm2home.farm.dto.response.FarmResponse;
import com.farm2home.farm.dto.response.HealthRecordResponse;
import com.farm2home.farm.dto.response.VaccinationResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Condition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface FarmMapper {

    @Mapping(target = "status", expression = "java(cow.getStatus().name())")
    CowResponse toCowResponse(Cow cow);

    @Mapping(target = "cowId",     expression = "java(vaccination.getCow().getId())")
    @Mapping(target = "tagNumber", expression = "java(vaccination.getCow().getTagNumber())")
    VaccinationResponse toVaccinationResponse(Vaccination vaccination);

    @Mapping(target = "cowId",     expression = "java(record.getCow().getId())")
    @Mapping(target = "tagNumber", expression = "java(record.getCow().getTagNumber())")
    @Mapping(target = "condition", expression = "java(record.getCondition().name())")
    HealthRecordResponse toHealthRecordResponse(HealthRecord record);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tagNumber", expression = "java(request.getTagNumber().toUpperCase())")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Cow toEntity(CreateCowRequest request);

    // hasText() below skips blank strings the same way the StringUtils.hasText guards it
    // replaces did; a plain null-check alone isn't equivalent.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tagNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateCowFromRequest(UpdateCowRequest request, @MappingTarget Cow cow);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cow", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Vaccination toEntity(CreateVaccinationRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cow", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateVaccinationFromRequest(UpdateVaccinationRequest request, @MappingTarget Vaccination vaccination);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cow", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    HealthRecord toEntity(CreateHealthRecordRequest request);

    FarmResponse toFarmResponse(Farm farm);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Farm toEntity(CreateFarmRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateFarmFromRequest(UpdateFarmRequest request, @MappingTarget Farm farm);

    @Condition
    default boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}

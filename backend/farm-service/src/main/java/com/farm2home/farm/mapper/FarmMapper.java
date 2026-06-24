package com.farm2home.farm.mapper;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.entity.HealthRecord;
import com.farm2home.farm.domain.entity.Vaccination;
import com.farm2home.farm.dto.response.CowResponse;
import com.farm2home.farm.dto.response.HealthRecordResponse;
import com.farm2home.farm.dto.response.VaccinationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}

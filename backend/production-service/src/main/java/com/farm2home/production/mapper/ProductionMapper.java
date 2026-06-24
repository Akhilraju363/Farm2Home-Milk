package com.farm2home.production.mapper;

import com.farm2home.production.domain.entity.MilkProduction;
import com.farm2home.production.dto.response.MilkProductionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductionMapper {

    @Mapping(target = "session",      expression = "java(entity.getSession().name())")
    @Mapping(target = "qualityGrade", expression = "java(entity.getQualityGrade() != null ? entity.getQualityGrade().name() : null)")
    MilkProductionResponse toResponse(MilkProduction entity);
}

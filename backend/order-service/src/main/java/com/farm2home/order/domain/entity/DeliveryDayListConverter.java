package com.farm2home.order.domain.entity;

import com.farm2home.order.domain.enums.DeliveryDay;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class DeliveryDayListConverter implements AttributeConverter<List<DeliveryDay>, String> {

    @Override
    public String convertToDatabaseColumn(List<DeliveryDay> days) {
        if (days == null || days.isEmpty()) return null;
        return days.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public List<DeliveryDay> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyList();
        return Arrays.stream(dbData.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DeliveryDay::valueOf)
                .toList();
    }
}

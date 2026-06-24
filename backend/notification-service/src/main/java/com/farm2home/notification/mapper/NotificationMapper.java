package com.farm2home.notification.mapper;

import com.farm2home.notification.domain.entity.NotificationLog;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "channel", expression = "java(log.getChannel().name())")
    @Mapping(target = "status",  expression = "java(log.getStatus().name())")
    NotificationLogResponse toResponse(NotificationLog log);
}

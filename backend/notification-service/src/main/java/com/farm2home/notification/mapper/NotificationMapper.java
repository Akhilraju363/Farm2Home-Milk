package com.farm2home.notification.mapper;

import com.farm2home.notification.domain.entity.NotificationLog;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import com.farm2home.notification.service.NotificationClassifier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = NotificationClassifier.class)
public interface NotificationMapper {

    @Mapping(target = "channel",  expression = "java(log.getChannel().name())")
    @Mapping(target = "status",   expression = "java(log.getStatus().name())")
    @Mapping(target = "type",     expression = "java(NotificationClassifier.typeOf(log.getEventType()).name())")
    @Mapping(target = "priority", expression = "java(NotificationClassifier.priorityOf(log.getEventType()).name())")
    @Mapping(target = "read",     source = "read")
    NotificationLogResponse toResponse(NotificationLog log);
}

package com.farm2home.common.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private boolean success = false;

    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;
}
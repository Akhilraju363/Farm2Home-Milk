package com.farm2home.common.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Standard envelope for every successful REST response, mirroring {@link ErrorResponse}'s
 * success/timestamp fields on the failure side so callers get a consistent shape either way
 * and never see a raw entity or bare DTO.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    private boolean success = true;

    private String message;

    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    private T data;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder().message(message).data(data).build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(null, data);
    }
}

package com.farm2home.customer.client;

import lombok.Data;

import java.util.List;

/** Minimal local projection of Spring Data's Page&lt;RouteResponse&gt; JSON shape - only the
 *  `content` array is needed here; Jackson ignores the rest (totalElements, totalPages, etc.). */
@Data
public class RoutePageDto {
    private List<RouteDto> content;
}

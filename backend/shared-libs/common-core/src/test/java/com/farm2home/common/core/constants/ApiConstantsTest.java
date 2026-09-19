package com.farm2home.common.core.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the shared public-endpoint list every service uses for {@code permitAll()}.
 * A regression here (re-adding a broad {@code /actuator/**}) would re-expose
 * {@code /actuator/env} - which leaks the resolved jwt.secret and datasource credentials.
 */
class ApiConstantsTest {

    @Test
    @DisplayName("only health/info actuator endpoints are public - never a broad /actuator/** wildcard")
    void actuatorExposure_isNarrow() {
        List<String> base = List.of(ApiConstants.PUBLIC_ENDPOINTS_BASE);

        assertThat(base).contains("/actuator/health", "/actuator/health/**", "/actuator/info");
        assertThat(base).doesNotContain("/actuator/**", "/actuator/*");
        assertThat(base).noneMatch(p -> p.equals("/actuator/env") || p.equals("/actuator/prometheus"));
    }
}

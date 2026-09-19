package com.farm2home.common.web.security;

import com.farm2home.common.core.constants.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewayTrustHeaderFilterTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private HttpServletRequest runFilterWith(GatewayTrust trust, MockHttpServletRequest request) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        new GatewayTrustHeaderFilter(trust).doFilter(request, response, chain);
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(captor.capture(), org.mockito.ArgumentMatchers.any());
        return captor.getValue();
    }

    @Test
    @DisplayName("enforcing + no secret on request → forged X-User-* are hidden from the chain")
    void enforcing_forgedHeadersStripped() throws Exception {
        MockHttpServletRequest forged = new MockHttpServletRequest("GET", "/api/v1/customers/me");
        forged.addHeader(HeaderConstants.X_USER_ID, "11111111-1111-1111-1111-111111111111");
        forged.addHeader(HeaderConstants.X_USER_MOBILE, "9876543210");
        forged.addHeader(HeaderConstants.X_USER_ROLES, "SUPER_ADMIN");

        HttpServletRequest seen = runFilterWith(new GatewayTrust("real-secret"), forged);

        assertThat(seen.getHeader(HeaderConstants.X_USER_ID)).isNull();
        assertThat(seen.getHeader(HeaderConstants.X_USER_ROLES)).isNull();
        assertThat(java.util.Collections.list(seen.getHeaderNames()))
                .doesNotContain(HeaderConstants.X_USER_ID, HeaderConstants.X_USER_ROLES);
    }

    @Test
    @DisplayName("enforcing + correct secret → identity headers pass through untouched")
    void enforcing_trustedRequestPassesThrough() throws Exception {
        MockHttpServletRequest genuine = new MockHttpServletRequest("GET", "/api/v1/customers/me");
        genuine.addHeader(HeaderConstants.X_INTERNAL_AUTH, "real-secret");
        genuine.addHeader(HeaderConstants.X_USER_ID, "11111111-1111-1111-1111-111111111111");
        genuine.addHeader(HeaderConstants.X_USER_ROLES, "CUSTOMER");

        HttpServletRequest seen = runFilterWith(new GatewayTrust("real-secret"), genuine);

        assertThat(seen.getHeader(HeaderConstants.X_USER_ID)).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(seen.getHeader(HeaderConstants.X_USER_ROLES)).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("not enforcing (no secret configured) → request passes through unchanged")
    void notEnforcing_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers/me");
        request.addHeader(HeaderConstants.X_USER_ID, "11111111-1111-1111-1111-111111111111");

        HttpServletRequest seen = runFilterWith(new GatewayTrust(""), request);

        assertThat(seen.getHeader(HeaderConstants.X_USER_ID)).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(seen).isSameAs(request);
    }
}

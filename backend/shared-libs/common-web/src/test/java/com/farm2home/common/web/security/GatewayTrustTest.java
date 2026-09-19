package com.farm2home.common.web.security;

import com.farm2home.common.core.constants.HeaderConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayTrustTest {

    @Test
    @DisplayName("no secret configured → not enforcing, every request trusted (dev/test parity)")
    void blankSecret_notEnforcing() {
        GatewayTrust trust = new GatewayTrust("  ");
        assertThat(trust.isEnforcing()).isFalse();
        assertThat(trust.isTrusted(new MockHttpServletRequest())).isTrue();
    }

    @Test
    @DisplayName("secret configured, request has no X-Internal-Auth → NOT trusted (forged direct call)")
    void enforcing_missingHeader_notTrusted() {
        GatewayTrust trust = new GatewayTrust("real-secret");
        MockHttpServletRequest forged = new MockHttpServletRequest();
        forged.addHeader(HeaderConstants.X_USER_ID, "11111111-1111-1111-1111-111111111111");
        forged.addHeader(HeaderConstants.X_USER_ROLES, "SUPER_ADMIN");

        assertThat(trust.isTrusted(forged)).isFalse();
    }

    @Test
    @DisplayName("secret configured, wrong X-Internal-Auth → NOT trusted")
    void enforcing_wrongSecret_notTrusted() {
        GatewayTrust trust = new GatewayTrust("real-secret");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HeaderConstants.X_INTERNAL_AUTH, "guessed-secret");

        assertThat(trust.isTrusted(req)).isFalse();
    }

    @Test
    @DisplayName("secret configured, correct X-Internal-Auth → trusted (genuine gateway hop)")
    void enforcing_correctSecret_trusted() {
        GatewayTrust trust = new GatewayTrust("real-secret");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HeaderConstants.X_INTERNAL_AUTH, "real-secret");

        assertThat(trust.isTrusted(req)).isTrue();
        assertThat(trust.isEnforcing()).isTrue();
        assertThat(trust.secret()).isEqualTo("real-secret");
    }
}

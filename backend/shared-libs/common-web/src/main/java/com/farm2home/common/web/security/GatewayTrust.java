package com.farm2home.common.web.security;

import com.farm2home.common.core.constants.HeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Decides whether an inbound request may be trusted to carry gateway-issued identity
 * ({@code X-User-Id}/{@code X-User-Mobile}/{@code X-User-Roles}).
 *
 * <p>The api-gateway sets {@link HeaderConstants#X_INTERNAL_AUTH} to a shared secret on every
 * request it forwards (after stripping any client-supplied copy). A downstream service trusts
 * the identity headers only when that header matches its own configured secret. A request that
 * reaches a service directly - bypassing the gateway - cannot know the secret, so its forged
 * {@code X-User-*} headers are ignored and it is treated as anonymous.
 *
 * <p><b>Backward compatibility:</b> when no secret is configured (local dev, CI, tests) the
 * check is disabled and every request is trusted, exactly as before this mechanism existed.
 * Production must configure a secret - {@code JwtSecretGuard}-style startup guards in the
 * gateway/auth-service enforce the {@code prod} profile side; here a blank secret simply means
 * "not enforcing".
 *
 * <p>Registered as a bean by {@code CommonWebAutoConfiguration}; not a {@code @Component}
 * because it lives outside any consuming service's component-scan base package.
 */
public class GatewayTrust {

    private static final Logger log = LoggerFactory.getLogger(GatewayTrust.class);

    private final String secret;

    public GatewayTrust(String secret) {
        this.secret = secret == null ? "" : secret.trim();
        if (this.secret.isEmpty()) {
            log.warn("GatewayTrust is NOT enforcing gateway origin (no internal secret configured). "
                    + "Acceptable for local/dev/test only - set FARM2HOME_GATEWAY_INTERNAL_SECRET in production.");
        }
    }

    /** True when a secret is configured and the gateway-origin header is therefore required. */
    public boolean isEnforcing() {
        return !secret.isEmpty();
    }

    /** The configured secret (empty string when not enforcing) - used by
     *  {@code RequestHeaderForwarder} to attach it to inter-service calls. */
    public String secret() {
        return secret;
    }

    /**
     * @return {@code true} if this request may carry trusted {@code X-User-*} headers:
     *         either enforcement is disabled, or the request presents the correct
     *         {@link HeaderConstants#X_INTERNAL_AUTH} secret.
     */
    public boolean isTrusted(HttpServletRequest request) {
        if (!isEnforcing()) {
            return true;
        }
        String presented = request.getHeader(HeaderConstants.X_INTERNAL_AUTH);
        return matches(presented);
    }

    boolean matches(String presented) {
        if (!StringUtils.hasText(presented)) {
            return false;
        }
        byte[] a = presented.getBytes(StandardCharsets.UTF_8);
        byte[] b = secret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}

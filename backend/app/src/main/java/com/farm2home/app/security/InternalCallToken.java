package com.farm2home.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Proof-of-origin secret for in-process {@code lb://} loopback calls — the single-JVM analogue of
 * the api-gateway's {@link com.farm2home.common.core.constants.HeaderConstants#X_INTERNAL_AUTH}
 * shared secret ({@code common-web}'s {@code GatewayTrust}).
 *
 * <p>In the microservice deployment the gateway stamps {@code X-Internal-Auth} on every request it
 * forwards and each service trusts {@code X-User-*} identity headers only when that secret matches
 * — a request that reaches a service directly cannot know the secret, so its forged {@code X-User-*}
 * headers are ignored. There is no gateway here, but two embedded services still make in-process
 * calls that carry a <b>fixed system identity</b> via {@code X-User-*} headers rather than a bearer
 * token:
 *
 * <ul>
 *   <li><i>invoice-service</i>'s order/payment/customer clients ({@code SystemIdentityHeaders}) —
 *       they read records the original caller's role may be too narrow for;</li>
 *   <li><i>notification-service</i>'s {@code CustomerServiceClient} — called from a Kafka consumer
 *       thread with no per-request identity to forward.</li>
 * </ul>
 *
 * <p>{@link com.farm2home.app.config.LoadBalancerClientConfig}'s loopback filter stamps this
 * secret on every rewritten {@code lb://} request; {@link SpikeJwtAuthenticationFilter} trusts an
 * inbound {@code X-User-*} system identity <b>only</b> when the request also presents this exact
 * secret (constant-time compared) <i>and</i> arrived over the loopback interface. A forged
 * {@code X-User-*} from any external client — or any local client that does not know the
 * per-process secret — cannot authenticate.
 *
 * <p>The secret defaults to a fresh 256-bit random value generated at startup: it never leaves the
 * container, is not in source or config, and changes every restart. {@code FARM2HOME_APP_INTERNAL_SECRET}
 * can pin it if ever needed, but the default is strictly stronger than a static shared secret.
 */
@Component
public class InternalCallToken {

    private static final Logger log = LoggerFactory.getLogger(InternalCallToken.class);

    private final String secret;

    public InternalCallToken(@Value("${farm2home.app.internal-secret:}") String configured) {
        if (StringUtils.hasText(configured)) {
            this.secret = configured.trim();
            log.info("In-process loopback trust: using configured farm2home.app.internal-secret");
        } else {
            byte[] rnd = new byte[32];
            new SecureRandom().nextBytes(rnd);
            this.secret = HexFormat.of().formatHex(rnd);
            log.info("In-process loopback trust: generated a per-process internal secret "
                    + "(X-User-* system identity is accepted only with this secret + a loopback peer)");
        }
    }

    /** The secret to stamp on outbound in-process {@code lb://} calls. */
    public String value() {
        return secret;
    }

    /** Constant-time check of an inbound {@code X-Internal-Auth} header value. */
    public boolean matches(String presented) {
        if (!StringUtils.hasText(presented)) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
    }
}

package com.farm2home.gateway.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fail-closed check for the {@code prod} profile: refuses to start if {@code jwt.secret} is
 * missing or is still the placeholder value committed to the repository for local development.
 * The gateway verifies every request's token with this key, so a wrong/absent value would let
 * anyone mint valid admin tokens.
 *
 * <p>Inert in every other profile, so local/dev/test/docker behaviour is unchanged.
 */
@Configuration
@Profile("prod")
public class JwtSecretGuard {

    static final String COMMITTED_DEV_SECRET =
            "ZmFybTJob21lLXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private final String secret;

    public JwtSecretGuard(@Value("${jwt.secret:}") String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    @PostConstruct
    void verify() {
        if (secret.isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set in production (profile 'prod'). Refusing to start with no signing key.");
        }
        if (COMMITTED_DEV_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the committed development placeholder. Set a unique random secret "
                            + "(>=256-bit, base64) via the environment before deploying to production.");
        }
    }
}

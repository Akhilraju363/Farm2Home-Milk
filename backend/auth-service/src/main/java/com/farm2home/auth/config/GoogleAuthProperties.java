package com.farm2home.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code google.*} - only a client ID is ever needed here (see
 *  GoogleTokenValidator's javadoc for why no client secret exists in this codebase at all). */
@Data
@ConfigurationProperties(prefix = "google")
public class GoogleAuthProperties {

    /** The OAuth client ID Google Identity Services issues the ID token for - GoogleTokenValidator
     *  rejects any token whose audience doesn't match this exactly. Blank (default) disables
     *  Google Sign-In entirely - see GoogleTokenValidator.validate(). */
    private String clientId = "";
}

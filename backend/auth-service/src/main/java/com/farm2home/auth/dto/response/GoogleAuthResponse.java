package com.farm2home.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Either a normal Farm2Home auth response (existing account found/linked - "
        + "registrationRequired=false, auth populated), or a signal that no Farm2Home account "
        + "exists yet for this Google identity (registrationRequired=true, auth null) - the "
        + "frontend should continue into the existing registration wizard, pre-filled with "
        + "firstName/lastName/email, rather than treating this as an error.")
public class GoogleAuthResponse {

    private boolean registrationRequired;

    /** Populated only when registrationRequired=false - identical shape to a normal login/register
     *  response, since Google sign-in reuses the exact same token-issuance mechanism. */
    private AuthResponse auth;

    /** Populated only when registrationRequired=true, sourced from the validated Google credential
     *  (never trusted from the request otherwise) purely to pre-fill the registration form. */
    private String firstName;
    private String lastName;
    private String email;
}

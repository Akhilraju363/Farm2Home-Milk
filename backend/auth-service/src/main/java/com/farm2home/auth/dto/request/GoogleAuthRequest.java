package com.farm2home.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleAuthRequest {

    @NotBlank(message = "Google credential is required")
    @Schema(description = "The ID token returned by Google Identity Services on the frontend "
            + "(google.accounts.id's callback response.credential) - validated server-side against "
            + "Google's own public keys before any of its claims are trusted (see GoogleTokenValidator). "
            + "Never a plain email/name/user id supplied directly by the browser.",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...")
    private String credential;
}

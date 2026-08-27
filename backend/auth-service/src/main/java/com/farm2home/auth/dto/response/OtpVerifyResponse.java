package com.farm2home.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Result of verifying an OTP. For otpType=REGISTRATION/FORGOT_PASSWORD this "
        + "is always {registrationRequired: false, auth: null} - identical to the plain "
        + "success-with-no-data response this endpoint returned before Mobile OTP Login existed, so "
        + "existing callers (registration, forgot-password) are unaffected. For otpType=LOGIN: an "
        + "existing account signs the caller in immediately (auth populated, same shape as normal "
        + "login); a mobile number with no account yet returns registrationRequired=true so the "
        + "frontend can continue into the existing registration wizard instead of the account being "
        + "silently/incompletely created.")
public class OtpVerifyResponse {

    private boolean registrationRequired;

    private AuthResponse auth;
}

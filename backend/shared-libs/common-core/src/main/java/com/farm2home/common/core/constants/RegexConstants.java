package com.farm2home.common.core.constants;

/** Validation regex patterns repeated across multiple services' request DTOs. */
public final class RegexConstants {

    private RegexConstants() {
    }

    /** 10-digit Indian mobile number, starting 6-9. */
    public static final String MOBILE_PATTERN = "^[6-9]\\d{9}$";

    public static final String OTP_PATTERN = "^\\d{6}$";

    /** At least one lowercase, one uppercase, one digit, one of @$!%*?& */
    public static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$";
}

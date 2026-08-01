package com.farm2home.common.core.constants;

/** Numeric validation limits repeated identically across multiple services' request DTOs.
 *  Validation messages stay local to each DTO (they're field-specific, e.g. "Farm name"
 *  vs "Product name") - only the limit values themselves are centralized here. */
public final class ValidationConstants {

    private ValidationConstants() {
    }

    public static final int SHORT_NAME_MAX_LENGTH = 50;
    public static final int NAME_MAX_LENGTH = 100;
    public static final int NOTES_MAX_LENGTH = 255;

    public static final int PASSWORD_MIN_LENGTH = 8;

    public static final int PINCODE_MIN_LENGTH = 6;
    public static final int PINCODE_MAX_LENGTH = 10;

    public static final int OTP_LENGTH = 6;

    /** @DecimalMin's value attribute is a String, not a number. */
    public static final String MIN_ZERO = "0.0";
    public static final String MIN_POSITIVE_AMOUNT = "0.01";
    public static final String MIN_SUBSCRIPTION_QUANTITY = "0.5";
}

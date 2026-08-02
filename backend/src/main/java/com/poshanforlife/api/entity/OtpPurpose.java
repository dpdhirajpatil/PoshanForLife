package com.poshanforlife.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * What an issued OTP is allowed to do. Deliberately part of the lookup key
 * when verifying, so a code sent to prove ownership during signup can never be
 * replayed to log in (or vice versa).
 *
 * <p>Wire format is uppercase ("SIGNUP" | "LOGIN" | "ADD_PHONE"), matching the
 * value stored in {@code phone_otps.purpose}; parsing accepts any casing.
 */
public enum OtpPurpose {
    /** Create a brand-new LEAD account owning this phone. */
    SIGNUP,
    /** Authenticate an existing account that already verified this phone. */
    LOGIN,
    /** Attach a phone to the already-authenticated caller's account. */
    ADD_PHONE;

    @JsonValue
    public String toWire() {
        return name();
    }

    @JsonCreator
    public static OtpPurpose fromWire(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

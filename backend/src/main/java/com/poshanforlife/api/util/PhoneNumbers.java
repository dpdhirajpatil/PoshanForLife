package com.poshanforlife.api.util;

import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;

/**
 * Normalises phone numbers to E.164 before they're stored or compared.
 *
 * <p>This matters more than it looks: the uniqueness of a verified phone, the
 * "has this number already signed up" check, and the per-phone rate limit are
 * all string comparisons. Without one canonical form, "+91 98765 43210" and
 * "+919876543210" would be two different accounts.
 */
public final class PhoneNumbers {

    /** Default country dialling code applied to a bare local number. */
    private static final String DEFAULT_COUNTRY_CODE = "91";

    private PhoneNumbers() {
    }

    /**
     * @return the number as {@code +<digits>}
     * @throws ApiException VALIDATION_ERROR if it can't be a real phone number
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A phone number is required");
        }

        String trimmed = raw.trim();
        boolean explicitPlus = trimmed.startsWith("+");
        // Strip spaces, dashes, brackets — anything that isn't a digit.
        String digits = trimmed.replaceAll("\\D", "");

        if (digits.startsWith("00")) {
            // 00 is the other common international prefix; treat it as "+".
            digits = digits.substring(2);
            explicitPlus = true;
        }
        if (!explicitPlus && digits.length() == 10) {
            // A bare local number — assume the default country.
            digits = DEFAULT_COUNTRY_CODE + digits;
        }

        // E.164 allows at most 15 digits; anything under 8 can't be a real
        // subscriber number with a country code attached.
        if (digits.length() < 8 || digits.length() > 15) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Enter a valid phone number");
        }
        return "+" + digits;
    }
}

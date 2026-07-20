package com.poshanforlife.api.util;

import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;

import java.util.Locale;
import java.util.function.Function;

/** Parses an optional lowercase wire-format query param into an enum, or 422s with a friendly message. */
public final class WireEnums {

    private WireEnums() {
    }

    public static <E> E parse(String value, Function<String, E> parser, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}

package com.poshanforlife.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-client-IP token-bucket rate limit for a controller method.
 * Exceeding it yields 429 RATE_LIMIT_EXCEEDED in the standard envelope.
 * Example (reports upload, later prompt): {@code @RateLimit(requests = 10, windowSeconds = 3600)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Number of requests allowed per window. */
    int requests();

    /** Window length in seconds. */
    int windowSeconds();
}

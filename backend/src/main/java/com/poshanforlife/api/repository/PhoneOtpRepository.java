package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.OtpPurpose;
import com.poshanforlife.api.entity.PhoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, UUID> {

    /**
     * The OTP a verify attempt is checked against: the newest still-unverified
     * code for this phone+purpose. Requesting a new code therefore supersedes
     * any earlier one without needing to delete it.
     */
    Optional<PhoneOtp> findFirstByPhoneAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(
            String phone, OtpPurpose purpose);

    /**
     * Requests issued for a phone since a cutoff — the per-phone send limit.
     * Counted in the database rather than an in-memory bucket so the limit
     * survives a restart and holds across instances (unlike the IP-keyed
     * {@code @RateLimit} interceptor, which is per-process by design).
     */
    long countByPhoneAndCreatedAtAfter(String phone, Instant cutoff);
}

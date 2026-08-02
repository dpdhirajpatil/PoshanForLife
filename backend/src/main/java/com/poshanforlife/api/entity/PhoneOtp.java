package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A single issued one-time password. The code is bcrypt-hashed the same way a
 * password is — {@link #otpHash} is never the raw digits, so leaking this table
 * doesn't hand out live login codes.
 *
 * <p>Rows are kept after use rather than deleted: {@code verified} flips to
 * true and the row becomes an audit trail of when a number was proven.
 */
@Getter
@Setter
@Entity
@Table(name = "phone_otps")
public class PhoneOtp extends BaseEntity {

    /** E.164, normalised before storage — see PhoneNumbers.normalize. */
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Wrong guesses so far; at {@code MAX_ATTEMPTS} this OTP is spent. */
    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private boolean verified = false;

    /** Set for ADD_PHONE only — the user attaching this number. Null otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}

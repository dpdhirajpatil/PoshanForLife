package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /**
     * Stored lowercase; uniqueness enforced by the DB. Null for an OTP-only
     * account created via phone signup — a DB CHECK guarantees such a user
     * always has a verified phone instead, so no account is ever left with
     * no way to identify itself.
     */
    @Column(unique = true)
    private String email;

    /** Null for phone-only accounts, which authenticate by OTP and have no password. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(length = 32)
    private String phone;

    /**
     * True once an OTP sent to {@link #phone} has been confirmed. Only verified
     * phones are unique (partial unique index) and only a verified phone can be
     * used to log in — an unverified number is just unproven profile text.
     */
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** Set by the Android app on login and again on onNewToken; null = push disabled for this user. */
    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Soft delete: DELETE /users/{id} flips this to false, rows are never removed. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_prefs", nullable = false)
    private NotificationPrefs notificationPrefs = NotificationPrefs.defaults();
}

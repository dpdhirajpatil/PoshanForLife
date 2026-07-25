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

    /** Stored lowercase; uniqueness enforced by the DB. */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(length = 32)
    private String phone;

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

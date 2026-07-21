package com.poshanforlife.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * In-app notification, created exclusively through NotificationService
 * (never by constructing this entity directly elsewhere) so notificationPrefs
 * gating stays centralized. relatedEntityType/Id are optional deep-link
 * targets — e.g. type="lead"/id=the lead's id — resolved by the frontend
 * into a route.
 */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    public static final String TYPE_PATIENT_ASSIGNED = "PATIENT_ASSIGNED";
    public static final String TYPE_LEAD_FOLLOWUP = "LEAD_FOLLOWUP";
    public static final String TYPE_PROCESSING_ERROR = "PROCESSING_ERROR";
    public static final String TYPE_SYSTEM_ANNOUNCEMENT = "SYSTEM_ANNOUNCEMENT";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "related_entity_type", length = 32)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;
}

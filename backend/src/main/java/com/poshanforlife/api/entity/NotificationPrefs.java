package com.poshanforlife.api.entity;

/**
 * Per-user notification preferences, persisted as a jsonb column on users.
 * Immutable — merge partial updates with {@link #merge}.
 */
public record NotificationPrefs(
        boolean inbodyReport,
        boolean patientAssigned,
        boolean processingErrors,
        boolean systemAnnouncements) {

    public static NotificationPrefs defaults() {
        return new NotificationPrefs(true, true, true, true);
    }

    /** Returns a copy with the non-null fields overwritten. */
    public NotificationPrefs merge(Boolean inbodyReport, Boolean patientAssigned,
                                   Boolean processingErrors, Boolean systemAnnouncements) {
        return new NotificationPrefs(
                inbodyReport != null ? inbodyReport : this.inbodyReport,
                patientAssigned != null ? patientAssigned : this.patientAssigned,
                processingErrors != null ? processingErrors : this.processingErrors,
                systemAnnouncements != null ? systemAnnouncements : this.systemAnnouncements);
    }
}

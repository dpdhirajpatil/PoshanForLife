package com.poshanforlife.api.dto;

/** Partial update — null fields keep their current value (merge semantics). */
public record NotificationPrefsRequest(
        Boolean inbodyReport,
        Boolean patientAssigned,
        Boolean processingErrors,
        Boolean systemAnnouncements) {
}

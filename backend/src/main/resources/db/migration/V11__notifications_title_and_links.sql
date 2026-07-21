-- Notifications feature: adds the fields the minimal prompt-1/04/11 rows
-- didn't need yet — a short title (list/dropdown display) and an optional
-- deep-link target (relatedEntityType/Id, e.g. "lead"/leadId).

ALTER TABLE notifications
    ADD COLUMN title               varchar(255),
    ADD COLUMN related_entity_type varchar(32),
    ADD COLUMN related_entity_id   uuid;

UPDATE notifications
SET title = CASE type
    WHEN 'PATIENT_ASSIGNED' THEN 'New patient assigned'
    WHEN 'LEAD_FOLLOWUP' THEN 'Follow-up reminder'
    ELSE 'Notification'
END
WHERE title IS NULL;

ALTER TABLE notifications ALTER COLUMN title SET NOT NULL;

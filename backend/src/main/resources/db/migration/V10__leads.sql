-- Leads / CRM feature.
--
-- leads is the pipeline entity (NEW..CONVERTED/LOST); lead_activities is its
-- timeline (notes/calls/whatsapp/estimate_sent, plus auto-created
-- stage_change and converted entries). converted_patient_id links a
-- converted lead to the User created by the convert flow.

CREATE TABLE leads (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                      varchar(255) NOT NULL,
    phone                     varchar(32),
    email                     varchar(255),
    city                      varchar(100),
    age                       integer,
    gender                    varchar(16),
    source                    varchar(32)  NOT NULL,
    health_goal               text,
    notes                     text,
    stage                     varchar(16)  NOT NULL DEFAULT 'NEW',
    assigned_practitioner_id  uuid REFERENCES users (id),
    interested_programme_id   uuid REFERENCES programmes (id),
    next_followup_at          timestamptz,
    converted_patient_id      uuid REFERENCES users (id),
    created_by                uuid         NOT NULL REFERENCES users (id),
    created_at                timestamptz  NOT NULL DEFAULT now(),
    updated_at                timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_leads_stage ON leads (stage);
CREATE INDEX idx_leads_assigned_practitioner ON leads (assigned_practitioner_id);
CREATE INDEX idx_leads_next_followup ON leads (next_followup_at) WHERE next_followup_at IS NOT NULL;

CREATE TABLE lead_activities (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id        uuid        NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    activity_type  varchar(16) NOT NULL,
    description    text        NOT NULL,
    old_stage      varchar(16),
    new_stage      varchar(16),
    created_by     uuid        NOT NULL REFERENCES users (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_lead_activities_lead ON lead_activities (lead_id, created_at);

-- Documents feature: draft estimates and GST-aware invoices, extending
-- prompt 08's Transaction/invoice-number machinery for the mobile app's
-- RN-20 module. items is the line-item breakdown (jsonb array); GST
-- amounts/subtotal/total are computed at read time, not stored.
-- estimate_counters mirrors invoice_counters (V7) but is its own series —
-- Document invoices reuse invoice_counters directly via
-- TransactionNumbers.nextInvoiceNumber() so invoice numbers stay one
-- globally-unique series regardless of which table created them.

CREATE TABLE documents (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type    varchar(16)   NOT NULL,
    document_number  varchar(20)   NOT NULL UNIQUE,
    lead_id          uuid REFERENCES leads (id),
    patient_id       uuid REFERENCES users (id),
    status           varchar(16)   NOT NULL DEFAULT 'DRAFT',
    items            jsonb         NOT NULL,
    discount_inr     numeric(10,2) NOT NULL DEFAULT 0,
    notes            text,
    valid_for_days   integer,
    pdf_object_path  varchar(500),
    created_by       uuid          NOT NULL REFERENCES users (id),
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT chk_documents_one_subject CHECK (
        (lead_id IS NOT NULL AND patient_id IS NULL)
        OR (lead_id IS NULL AND patient_id IS NOT NULL)
    )
);

CREATE INDEX idx_documents_lead ON documents (lead_id);
CREATE INDEX idx_documents_patient ON documents (patient_id);
CREATE INDEX idx_documents_type_status ON documents (document_type, status);

CREATE TABLE estimate_counters (
    month_key  varchar(6) PRIMARY KEY,
    next_value integer NOT NULL
);

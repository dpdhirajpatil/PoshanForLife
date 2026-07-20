-- Transactions & Invoices feature: manual entry (ADMIN) and the future
-- payment-gateway webhook variant both accept an optional gateway reference
-- and free-text notes, which the original entity list didn't spell out but
-- the POST body requires.

ALTER TABLE transactions
    ADD COLUMN payment_gateway_ref varchar(255),
    ADD COLUMN notes               text;

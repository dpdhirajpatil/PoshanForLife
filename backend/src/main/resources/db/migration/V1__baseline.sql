-- Baseline migration. Feature prompts add V2__, V3__, ... on top.
-- Enable pgcrypto for gen_random_uuid() used as entity primary keys.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

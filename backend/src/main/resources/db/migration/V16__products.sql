-- Products feature: physical goods sold to patients (meal replacements,
-- supplements, etc.) — distinct from Catalogue's services/programmes.
-- Segments are admin-manageable categories, not a fixed enum, so the
-- lineup can keep growing. No purchase/cart/inventory tables yet — price
-- is stored now purely as informational display data for a future
-- "Purchase" prompt to build on top of without restructuring this table.

CREATE TABLE product_segments (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name          varchar(255) NOT NULL,
    display_order integer      NOT NULL DEFAULT 0,
    status        varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_product_segments_name ON product_segments (lower(name));

CREATE TABLE products (
    id            uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    segment_id    uuid           NOT NULL REFERENCES product_segments (id),
    name          varchar(255)   NOT NULL,
    description   text,
    -- Array of Supabase Storage public URLs; JSON rather than a native
    -- Postgres array to match this codebase's existing list-column
    -- convention (see reports.parsed_data / users.notification_prefs).
    images        jsonb          NOT NULL DEFAULT '[]',
    price_inr     numeric(10, 2),
    sku           varchar(64),
    status        varchar(16)    NOT NULL DEFAULT 'DRAFT',
    display_order integer        NOT NULL DEFAULT 0,
    created_by    uuid           NOT NULL REFERENCES users (id),
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now()
);

-- No ON DELETE CASCADE from products to product_segments: segment deletion
-- is blocked at the service layer while it still has any products, so a
-- plain FK (default RESTRICT) is the correct backstop, not a cascade.
CREATE INDEX idx_products_segment ON products (segment_id, display_order);
CREATE INDEX idx_products_status ON products (status);

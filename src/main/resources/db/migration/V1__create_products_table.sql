CREATE TABLE products
(
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(150)  NOT NULL,
    description    VARCHAR(2000) NOT NULL,
    price          NUMERIC(10, 2) NOT NULL,
    stock_quantity INTEGER       NOT NULL,
    weight_grams   INTEGER       NOT NULL,
    spice_level    VARCHAR(20)   NOT NULL,
    image_url      VARCHAR(500),
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_products_price
        CHECK (price > 0),

    CONSTRAINT chk_products_stock
        CHECK (stock_quantity >= 0),

    CONSTRAINT chk_products_weight
        CHECK (weight_grams > 0),

    CONSTRAINT chk_products_spice_level
        CHECK (spice_level IN ('MILD', 'MEDIUM', 'HOT'))
);

CREATE INDEX idx_products_active
    ON products (active);

CREATE INDEX idx_products_name
    ON products (name);
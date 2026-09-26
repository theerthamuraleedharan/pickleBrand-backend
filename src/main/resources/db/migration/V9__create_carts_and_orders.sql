-- Product IDs deliberately survive product deletion so carts can report unavailable
-- items and historical orders retain their original references and snapshots.
CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_cart_user_product UNIQUE (user_id, product_id)
);

CREATE TABLE cart_imports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    migration_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT uk_cart_import UNIQUE (user_id, migration_id)
);

CREATE TABLE customer_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key UUID NOT NULL,
    address_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('AWAITING_QUOTE')),
    payment_status VARCHAR(30) NOT NULL CHECK (payment_status IN ('UNPAID')),
    subtotal NUMERIC(24,2) NOT NULL CHECK (subtotal >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'INR'),
    recipient_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    CONSTRAINT uk_order_idempotency UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_orders_user_created ON customer_orders(user_id, created_at DESC, id DESC);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    weight_grams INTEGER NOT NULL CHECK (weight_grams > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_order_product UNIQUE (order_id, product_id)
);

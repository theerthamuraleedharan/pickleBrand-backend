ALTER TABLE customer_orders
    DROP CONSTRAINT IF EXISTS customer_orders_status_check;

UPDATE customer_orders
SET status = 'PLACED'
WHERE status = 'AWAITING_QUOTE';

ALTER TABLE customer_orders
    ADD CONSTRAINT ck_customer_orders_status
        CHECK (status IN ('PLACED'));

ALTER TABLE customer_orders
    ADD COLUMN billing_address_id BIGINT,
    ADD COLUMN billing_recipient_name VARCHAR(150),
    ADD COLUMN billing_phone VARCHAR(30),
    ADD COLUMN billing_address_line_1 VARCHAR(255),
    ADD COLUMN billing_address_line_2 VARCHAR(255),
    ADD COLUMN billing_city VARCHAR(100),
    ADD COLUMN billing_state VARCHAR(100),
    ADD COLUMN billing_postal_code VARCHAR(20),
    ADD COLUMN billing_country VARCHAR(100),
    ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH_ON_DELIVERY',
    ADD COLUMN delivery_charge NUMERIC(24, 2) NOT NULL DEFAULT 0,
    ADD COLUMN tax NUMERIC(24, 2) NOT NULL DEFAULT 0,
    ADD COLUMN total NUMERIC(24, 2);

UPDATE customer_orders
SET billing_address_id = address_id,
    billing_recipient_name = recipient_name,
    billing_phone = phone,
    billing_address_line_1 = address_line_1,
    billing_address_line_2 = address_line_2,
    billing_city = city,
    billing_state = state,
    billing_postal_code = postal_code,
    billing_country = country,
    total = subtotal + delivery_charge + tax;

ALTER TABLE customer_orders
    ALTER COLUMN billing_address_id SET NOT NULL,
    ALTER COLUMN billing_recipient_name SET NOT NULL,
    ALTER COLUMN billing_phone SET NOT NULL,
    ALTER COLUMN billing_address_line_1 SET NOT NULL,
    ALTER COLUMN billing_city SET NOT NULL,
    ALTER COLUMN billing_postal_code SET NOT NULL,
    ALTER COLUMN billing_country SET NOT NULL,
    ALTER COLUMN total SET NOT NULL,
    ADD CONSTRAINT ck_customer_orders_payment_method
        CHECK (payment_method IN ('CASH_ON_DELIVERY')),
    ADD CONSTRAINT ck_customer_orders_delivery_charge
        CHECK (delivery_charge >= 0),
    ADD CONSTRAINT ck_customer_orders_tax
        CHECK (tax >= 0),
    ADD CONSTRAINT ck_customer_orders_total
        CHECK (total = subtotal + delivery_charge + tax);


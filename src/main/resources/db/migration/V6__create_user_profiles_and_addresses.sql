CREATE TABLE user_profiles
(
    user_id            BIGINT PRIMARY KEY,
    phone              VARCHAR(30),
    profile_image_name VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE addresses
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    recipient_name   VARCHAR(150) NOT NULL,
    phone            VARCHAR(30)  NOT NULL,
    address_line_1   VARCHAR(255) NOT NULL,
    address_line_2   VARCHAR(255),
    city             VARCHAR(100) NOT NULL,
    state            VARCHAR(100),
    postal_code      VARCHAR(20)  NOT NULL,
    country          VARCHAR(100) NOT NULL,
    default_address  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_addresses_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_addresses_user_id
    ON addresses (user_id);

CREATE UNIQUE INDEX uk_addresses_one_default_per_user
    ON addresses (user_id)
    WHERE default_address = TRUE;
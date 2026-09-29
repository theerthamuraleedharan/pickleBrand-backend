-- Keep numeric users.id unchanged so carts, addresses and orders retain ownership.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE external_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_external_identity UNIQUE (issuer, subject),
    CONSTRAINT uk_user_identity_per_issuer UNIQUE (user_id, issuer)
);

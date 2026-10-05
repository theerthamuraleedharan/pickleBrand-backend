-- OPERATOR-ONLY procedure, after verifying ownership of BOTH accounts.
-- Email equality is not sufficient. Get subject from the trusted Keycloak admin console.
-- Supply psql variables local_user_id, issuer and subject as described in OIDC_GUIDE.md.
-- No ON CONFLICT UPDATE: a mistaken attempt must fail instead of transferring ownership.
\set ON_ERROR_STOP on
BEGIN;
SELECT id, email FROM users WHERE id = :'local_user_id'::bigint FOR UPDATE;
INSERT INTO external_identities (user_id, issuer, subject)
VALUES (:'local_user_id'::bigint, :'issuer', :'subject');
COMMIT;

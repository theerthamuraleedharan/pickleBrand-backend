# Paste this prompt into the React project's VS Code assistant

Implement OIDC login with Keycloak in this React + TypeScript application. Inspect its
existing architecture first. The matching Spring Boot backend already supports OIDC;
do not modify backend files. Add comments explaining the authentication flow so I can
present it in a software-development interview.

Read the backend handoff if accessible:
`D:/Projects/Sujus Pickle/pickle-backend/OIDC_GUIDE.md`.

Backend/Keycloak contract:
- React: `http://localhost:5173`; `/api` proxies to `http://localhost:8080`.
- Keycloak URL: `http://localhost:8081`.
- Realm: `sujus-pickle`; public browser client: `pickle-react`; API audience: `pickle-api`.
- Login redirect URI: exactly `http://localhost:5173/oidc/callback`.
- Logout redirect URI: exactly `http://localhost:5173/login`.
- Flow: Authorization Code with PKCE S256, scopes `openid profile email`.
- No browser client secret. Password and implicit grants are disabled.
- OIDC backend mode: `SPRING_PROFILES_ACTIVE=oidc`.
- All protected APIs accept `Authorization: Bearer <access_token>`.
- `GET /api/auth/me` returns `{id, firstName, lastName, email, role}`. The `id` is the
  numeric LOCAL application user ID; use it for cart ownership/cache keys. Never use the
  external subject as the local ID or assume a `userId` claim exists in the token.
- `role` is `CUSTOMER` or `ADMIN`, based on the backend's verified API-client roles.
- Local `/api/auth/login`, `/register`, `/refresh`, `/logout` remain available in OIDC
  mode. The API validates local and Keycloak tokens with separate issuer-specific keys.
- First external login creates a local account if its verified email is new. An existing
  email without an explicit identity link returns 401 with a message containing
  "account linking". Display that message and offer sign-out/change-account; do not loop
  refreshing or relogging in. Linking is an operator task, not a frontend userId submission.
- Keycloak self-registration is disabled in this demo. Keep the normal local customer
  registration form available in OIDC mode; Keycloak accounts remain operator-provisioned.

Implementation requirements:
1. Use the official `keycloak-js` adapter. Add `VITE_AUTH_MODE=oidc` and the three public
   configuration values `VITE_OIDC_URL`, `VITE_OIDC_REALM`, `VITE_OIDC_CLIENT_ID` in
   `.env.oidc`. Preserve the current local authentication flow as the default mode.
2. Initialize one adapter instance with code flow, `pkceMethod: "S256"`, and `check-sso`.
   Make initialization single-flight and safe under React StrictMode. Process the callback
   before protected routes run, and handle loading, cancellation and initialization errors.
   Use the exact callback above for both login and check-sso. Do not configure an unregistered
   silent callback URL. Let the adapter manage state, nonce, verifier and callback validation.
3. Keep OIDC access/refresh/ID tokens only in the adapter's memory. Do not persist them
   in localStorage/sessionStorage or print them. Do not reuse legacy stored credentials in
   OIDC mode. Adapter-managed temporary protocol state during redirects is expected.
4. Integrate the existing AuthContext and Axios client. Before protected API calls, obtain
   the adapter access token and call `updateToken(30)` as needed. Let concurrent refreshes
   share one operation. Never send the ID token as an API bearer token.
   Use a reasonable API timeout (for example 10 seconds); the existing one-second timeout
   is too short for a first request that fetches signing keys and provisions an account.
5. Fetch `/api/auth/me` after login to populate AuthContext with the local user ID and role.
   Use this response for UI routing. Backend authorization remains authoritative.
6. On token-refresh failure, clear in-memory application authentication and show a sign-in
   action. Do not blindly replay POSTs, including checkout, after a 401 or network timeout.
   A 403 means insufficient permissions, not an instruction to refresh indefinitely.
7. Keep the local login and customer registration forms in both modes. When OIDC mode is
   enabled, offer Keycloak as an additional sign-in option. Support customer and admin
   login routes; an admin route still checks the backend user role. In local-only mode
   retain the current forms and session-storage behavior unchanged.
8. Implement Keycloak logout with the registered post-logout URI, clear user-specific UI
   state, and handle session expiry. Provide a way to sign out even if `/api/auth/me` fails.
9. Add `/oidc/callback` handling and safe post-login navigation. If remembering a destination,
   allow only internal application routes, never an arbitrary external return URL.
10. Keep product/cart/profile/order API shapes and business behavior unchanged. Do not migrate
    or delete the user's existing cart just to add authentication. Use the numeric local ID
    returned by `/api/auth/me` for any existing per-user cart storage.
11. Add focused tests for initialization/StrictMode, token attachment and refresh, roles,
    logout, and handling account-linking errors. Run the build and relevant tests. Report
    existing unrelated lint issues separately instead of rewriting unrelated UI code.
12. Add a short README explaining startup, the token flow, ID token versus access token,
    why a public client has no secret, and why PKCE is required. Explain that logout does
    not instantly revoke already-issued JWT access tokens; they expire after five minutes.

Demo credentials (local demo only):
- Customer: `demo-customer` / `Customer-demo-2026!`
- Store administrator: `demo-store-admin` / `Admin-demo-2026!`

Start command after implementation:
`npm run dev -- --mode oidc --port 5173 --strictPort`

Do the implementation, not just a plan. Explain what changed and how to demonstrate it.

package sujus.pickle.security.oidc;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import sujus.pickle.PostgresIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise the imported realm, browser code flow, PKCE, refresh and logout against real Keycloak. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("oidc")
class KeycloakCodeFlowTests extends PostgresIntegrationTest {
    private static final String CALLBACK = "http://localhost:5173/oidc/callback";
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.7.4")
            .withExposedPorts(8080)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("dev/keycloak/sujus-pickle-realm.json").toAbsolutePath()),
                    "/opt/keycloak/data/import/sujus-pickle-realm.json")
            .withCommand("start-dev", "--import-realm", "--hostname-strict=false")
            .waitingFor(Wait.forHttp("/realms/sujus-pickle/.well-known/openid-configuration").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3));
    static { KEYCLOAK.start(); }

    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry properties) {
        properties.add("app.oidc.issuer-uri", KeycloakCodeFlowTests::issuer);
        properties.add("app.oidc.jwk-set-uri", () -> issuer() + "/protocol/openid-connect/certs");
        properties.add("app.oidc.audience", () -> "pickle-api");
    }

    @Test
    void browserLoginRefreshLogoutAndAdminPermissions() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        BrowserFlow customer = authorize("demo-customer", "Customer-demo-2026!", true);
        HttpResponse<String> exchange = token(customer, customer.verifier());
        assertThat(exchange.statusCode()).isEqualTo(200);
        JsonNode tokens = mapper.readTree(exchange.body());
        String access = tokens.get("access_token").asText();
        String id = tokens.get("id_token").asText();
        assertThat(SignedJWT.parse(access).getJWTClaimsSet().getSubject()).isNotBlank();
        // An ID token describes the browser login; the API only accepts the access token.
        assertThat(SignedJWT.parse(id).getJWTClaimsSet().getStringClaim("nonce")).isEqualTo(customer.nonce());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + access)).andExpect(status().isForbidden());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + id)).andExpect(status().isUnauthorized());

        HttpResponse<String> refreshed = form(customer.client(), "token", Map.of("grant_type", "refresh_token",
                "client_id", "pickle-react", "refresh_token", tokens.get("refresh_token").asText()));
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String refresh = mapper.readTree(refreshed.body()).get("refresh_token").asText();
        assertThat(form(customer.client(), "logout", Map.of("client_id", "pickle-react", "refresh_token", refresh)).statusCode())
                .isEqualTo(204);
        assertThat(form(customer.client(), "token", Map.of("grant_type", "refresh_token", "client_id", "pickle-react",
                "refresh_token", refresh)).statusCode()).isEqualTo(400);

        BrowserFlow admin = authorize("demo-store-admin", "Admin-demo-2026!", true);
        JsonNode adminTokens = mapper.readTree(token(admin, admin.verifier()).body());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminTokens.get("access_token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void pkceIsRequiredAndWrongVerifierCannotRedeemCode() throws Exception {
        HttpClient client = browser();
        HttpResponse<String> missingPkce = client.send(HttpRequest.newBuilder(URI.create(issuer()
                + "/protocol/openid-connect/auth?" + encoded(Map.of("client_id", "pickle-react", "response_type", "code",
                "scope", "openid", "redirect_uri", CALLBACK)))).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(missingPkce.statusCode()).isIn(302, 400);
        assertThat(missingPkce.headers().firstValue("Location").orElse(missingPkce.body())).containsIgnoringCase("code_challenge");

        BrowserFlow flow = authorize("demo-customer", "Customer-demo-2026!", true);
        assertThat(token(flow, "wrong-verifier-abcdefghijklmnopqrstuvwxyz0123456789").statusCode()).isEqualTo(400);
        // Password grant is intentionally disabled. This demo proves browser OIDC, not a password-grant shortcut.
        assertThat(form(client, "token", Map.of("grant_type", "password", "client_id", "pickle-react",
                "username", "demo-customer", "password", "Customer-demo-2026!")).statusCode()).isEqualTo(400);
    }

    private BrowserFlow authorize(String username, String password, boolean pkce) throws Exception {
        HttpClient client = browser();
        String verifier = UUID.randomUUID().toString() + UUID.randomUUID();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        Map<String, String> parameters = new HashMap<>(Map.of("client_id", "pickle-react", "redirect_uri", CALLBACK,
                "response_type", "code", "scope", "openid profile email", "state", state, "nonce", nonce));
        if (pkce) { parameters.put("code_challenge", challenge); parameters.put("code_challenge_method", "S256"); }
        var page = client.send(HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/auth?" + encoded(parameters)))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.statusCode()).isEqualTo(200);
        // This is a loopback-only test browser. Real browsers treat localhost as a
        // trustworthy origin and support secure localhost cookies; Java CookieManager
        // filters them on plain HTTP. Emulate browser behavior here, without changing
        // any Keycloak cookie/security settings. Also use browser-style version-0 cookies.
        ((CookieManager) client.cookieHandler().orElseThrow()).getCookieStore().getCookies()
                .forEach(cookie -> { cookie.setVersion(0); cookie.setSecure(false); });
        var action = Pattern.compile("<form[^>]*action=\"([^\"]+)\"").matcher(page.body());
        assertThat(action.find()).as("Keycloak login form").isTrue();
        var login = client.send(HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded(Map.of("username", username, "password", password))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).withFailMessage("Keycloak rejected the test login: %s", login.body()).isEqualTo(302);
        URI location = URI.create(login.headers().firstValue("Location").orElseThrow());
        Map<String, String> query = new HashMap<>();
        for (String part : location.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        assertThat(query.get("state")).isEqualTo(state);
        assertThat(query.get("code")).isNotBlank();
        return new BrowserFlow(client, query.get("code"), verifier, nonce);
    }
    private HttpResponse<String> token(BrowserFlow flow, String verifier) throws Exception {
        return form(flow.client(), "token", Map.of("grant_type", "authorization_code", "client_id", "pickle-react",
                "code", flow.code(), "redirect_uri", CALLBACK, "code_verifier", verifier));
    }
    private static HttpResponse<String> form(HttpClient client, String endpoint, Map<String, String> values) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/" + endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded(values))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String encoded(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    private static HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private static String issuer() { return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/sujus-pickle"; }
    private record BrowserFlow(HttpClient client, String code, String verifier, String nonce) { }
}

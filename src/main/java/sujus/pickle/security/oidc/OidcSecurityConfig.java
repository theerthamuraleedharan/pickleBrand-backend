package sujus.pickle.security.oidc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import sujus.pickle.security.JwtService;
import java.util.*;

@Configuration
@Profile("oidc")
@EnableConfigurationProperties(OidcProperties.class)
public class OidcSecurityConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder(OidcProperties properties,
                         @Qualifier("localJwtDecoder") JwtDecoder localJwtDecoder) {
        // Keycloak keeps the private signing key. This API downloads ONLY public keys.
        // Nimbus caches the JWKS and fetches new keys when a new signing key ID appears.
        NimbusJwtDecoder oidcDecoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> apiToken = jwt -> {
            String subject = jwt.getSubject();
            boolean valid = jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                    && jwt.getExpiresAt() != null
                    && subject != null && !subject.isBlank() && subject.length() <= 255
                    // Keycloak uses typ=Bearer for ACCESS tokens; ID tokens are for the React client.
                    && "Bearer".equals(jwt.getClaimAsString("typ"));
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "An access token for this API is required", null));
        };
        // Signature verification alone is insufficient: check issuer, lifetime AND audience.
        oidcDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuerUri()), apiToken));
        return token -> {
            final String issuer;
            try {
                issuer = com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getIssuer();
            } catch (java.text.ParseException invalidToken) {
                throw new BadJwtException("Invalid bearer token", invalidToken);
            }

            if (properties.issuerUri().equals(issuer)) return oidcDecoder.decode(token);
            if (JwtService.ISSUER.equals(issuer)) return localJwtDecoder.decode(token);
            throw new BadJwtException("Untrusted token issuer");
        };
    }

    @Bean("applicationJwtAuthenticationConverter")
    Converter<Jwt, AbstractAuthenticationToken> oidcAuthenticationConverter(
            OidcIdentityService identities, OidcProperties properties) {
        JwtGrantedAuthoritiesConverter localAuthorities = new JwtGrantedAuthoritiesConverter();
        localAuthorities.setAuthoritiesClaimName("role");
        localAuthorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter localConverter = new JwtAuthenticationConverter();
        localConverter.setJwtGrantedAuthoritiesConverter(localAuthorities);

        return jwt -> {
            if (!properties.issuerUri().equals(jwt.getClaimAsString("iss"))) {
                return localConverter.convert(jwt);
            }
            // Trust only roles assigned to this API client, not realm-wide roles or a
            // user-editable top-level "role"/"userId" claim. Unknown roles grant nothing.
            Set<String> roles = new HashSet<>();
            Object access = jwt.getClaim("resource_access");
            if (access instanceof Map<?, ?> clients
                    && clients.get(properties.audience()) instanceof Map<?, ?> api
                    && api.get("roles") instanceof Collection<?> assigned) {
                for (Object role : assigned) {
                    if ("CUSTOMER".equals(role) || "ADMIN".equals(role)) roles.add((String) role);
                }
            }
            if (roles.isEmpty()) {
                throw new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException(
                        "This account has no application role");
            }
            var authorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
            return new OidcAuthenticationToken(jwt, authorities, identities.resolveUserId(jwt));
        };
    }
}

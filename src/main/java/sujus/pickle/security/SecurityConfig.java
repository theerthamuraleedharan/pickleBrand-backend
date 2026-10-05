package sujus.pickle.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;

import sujus.pickle.user.CustomUserDetailsService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey key = secretKey(properties);
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    @Profile("!oidc")
    JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = secretKey(properties);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256
                )
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    @Bean("localJwtDecoder")
    @Profile("oidc")
    JwtDecoder localJwtDecoder(JwtProperties properties) {
        SecretKey key = secretKey(properties);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    // The selected decoder verifies a token using only the key for its declared issuer.
    @Bean("applicationJwtAuthenticationConverter")
    @Profile("!oidc")
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return authenticationConverter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurityErrorHandler errors,
            @Qualifier("applicationJwtAuthenticationConverter")
            Converter<Jwt, ? extends AbstractAuthenticationToken> converter) throws Exception {

        return http
                // These APIs accept explicit Authorization headers, not login cookies.
                // If we later switch to cookie-based API authentication, revisit CSRF.
                .csrf(csrf -> csrf.disable())
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        .requestMatchers("/error")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products",
                                "/api/products/**"
                        ).permitAll()

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(resourceServer ->
                        resourceServer.authenticationEntryPoint(errors).accessDeniedHandler(errors).jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        converter
                                )
                        )
                )

                .build();
    }


    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key")
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        configuration.setExposedHeaders(List.of("Location", "Idempotency-Replayed"));

        source.registerCorsConfiguration(
                "/api/**",
                configuration
        );

        return source;
    }

    private SecretKey secretKey(JwtProperties properties) {
        byte[] keyBytes = properties
                .secret()
                .getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must contain at least 32 bytes"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}

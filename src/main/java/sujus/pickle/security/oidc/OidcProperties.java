package sujus.pickle.security.oidc;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.oidc")
public record OidcProperties(@NotBlank String issuerUri, @NotBlank String jwkSetUri,
                             @NotBlank String audience) { }

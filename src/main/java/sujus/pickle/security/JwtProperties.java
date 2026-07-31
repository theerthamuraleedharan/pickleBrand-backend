package sujus.pickle.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMinutes,
        long refreshTokenExpirationDays
) {
    public long accessTokenExpiresInSeconds() {
        return accessTokenExpirationMinutes * 60;
    }

    public long refreshTokenExpiresInSeconds() {
        return refreshTokenExpirationDays * 24 * 60 * 60;
    }
}
package sujus.pickle.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import sujus.pickle.user.AppUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {
    // This API signs its own JWTs for local login and refresh flows.
    public static final String ISSUER = "sujus-pickle-api";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtService(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(AppUser user) {
        Instant issuedAt = Instant.now();

        Instant expiresAt = issuedAt.plus(
                jwtProperties.accessTokenExpirationMinutes(),
                ChronoUnit.MINUTES
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("userId", user.getId())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("role", user.getRole().name())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}

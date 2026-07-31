package sujus.pickle.auth.token;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.security.JwtProperties;
import sujus.pickle.user.AppUser;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_SIZE_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public IssuedRefreshToken issue(AppUser user) {
        String rawToken = generateRandomToken();

        Instant expiresAt = Instant.now().plus(
                jwtProperties.refreshTokenExpirationDays(),
                ChronoUnit.DAYS
        );

        RefreshToken entity = new RefreshToken(
                user,
                hashToken(rawToken),
                expiresAt
        );

        refreshTokenRepository.save(entity);

        return new IssuedRefreshToken(
                rawToken,
                expiresAt
        );
    }

    @Transactional
    public RotationResult rotate(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken currentToken = refreshTokenRepository
                .findForUpdateByTokenHash(tokenHash)
                .orElseThrow(this::invalidRefreshToken);

        Instant now = Instant.now();

        if (currentToken.isRevoked()
                || currentToken.isExpired(now)) {
            throw invalidRefreshToken();
        }

        // The old refresh token can never be used again.
        currentToken.revoke();

        AppUser user = currentToken.getUser();
        IssuedRefreshToken newToken = issue(user);

        return new RotationResult(
                user,
                newToken
        );
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        refreshTokenRepository
                .findForUpdateByTokenHash(tokenHash)
                .ifPresent(RefreshToken::revoke);
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid, expired or revoked"
        );
    }

    public record IssuedRefreshToken(
            String value,
            Instant expiresAt
    ) {
    }

    public record RotationResult(
            AppUser user,
            IssuedRefreshToken refreshToken
    ) {
    }
}
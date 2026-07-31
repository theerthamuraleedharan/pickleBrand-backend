package sujus.pickle.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds,
        UserResponse user
) {

    public record UserResponse(
            Long id,
            String firstName,
            String lastName,
            String email,
            String role
    ) {
    }
}
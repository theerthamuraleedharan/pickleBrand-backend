package sujus.pickle.auth;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
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
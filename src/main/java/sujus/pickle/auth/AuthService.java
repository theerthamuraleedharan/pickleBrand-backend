package sujus.pickle.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.auth.token.RefreshTokenService;
import sujus.pickle.common.EmailAlreadyExistsException;
import sujus.pickle.security.*;
import sujus.pickle.user.*;

import java.util.Locale;

@Service
public class AuthService {

    // Handles local registration, login, refresh, and logout for application users.
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
    }

    // Registers a new local customer and issues both a JWT and a refresh token for the session.
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(
                    "An account already exists for this email. Please log in instead."
            );
        }

        AppUser user = new AppUser(
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.CUSTOMER
        );



        AppUser savedUser = userRepository.save(user);

        // Generate the access token before saving refresh token.
        String accessToken = jwtService.generateAccessToken(savedUser);

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.issue(savedUser);

        return createResponse(
                savedUser,
                accessToken,
                refreshToken.value()
        );
    }

    // Authenticates the user using the local password flow and rotates a new refresh token.
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken
                        .unauthenticated(
                                email,
                                request.password()
                        )
        );

        AppUser user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid email or password"
                        )
                );

        String accessToken =
                jwtService.generateAccessToken(user);

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.issue(user);

        return createResponse(
                user,
                accessToken,
                refreshToken.value()
        );
    }

    // Exchanges a valid refresh token for a new access token and a rotated refresh token.
    @Transactional
    public AuthResponse refresh(
            RefreshTokenRequest request
    ) {

        RefreshTokenService.RotationResult result =
                refreshTokenService.rotate(
                        request.refreshToken()
                );

        String accessToken =
                jwtService.generateAccessToken(result.user());

        return createResponse(
                result.user(),
                accessToken,
                result.refreshToken().value()
        );
    }

    // Revokes the supplied refresh token so the user cannot continue the session.
    @Transactional
    public void logout(
            RefreshTokenRequest request
    ) {
        refreshTokenService.revoke(
                request.refreshToken()
        );
    }

    private AuthResponse createResponse(
            AppUser user,
            String accessToken,
            String refreshToken
    ) {

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProperties.accessTokenExpiresInSeconds(),
                jwtProperties.refreshTokenExpiresInSeconds(),
                new AuthResponse.UserResponse(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getRole().name()
                )
        );
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}

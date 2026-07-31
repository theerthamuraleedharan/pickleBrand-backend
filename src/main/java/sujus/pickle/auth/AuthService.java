package sujus.pickle.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.auth.token.RefreshTokenService;
import sujus.pickle.security.*;
import sujus.pickle.user.*;

import javax.naming.AuthenticationException;
import java.util.Locale;

@Service
public class AuthService {

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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account already exists for this email"
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
package sujus.pickle.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CurrentUserController {

    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new CurrentUserResponse(
                jwt.getClaim("userId"),
                jwt.getClaimAsString("firstName"),
                jwt.getClaimAsString("lastName"),
                jwt.getSubject(),
                jwt.getClaimAsString("role")
        );
    }

    public record CurrentUserResponse(
            Long id,
            String firstName,
            String lastName,
            String email,
            String role
    ) {
    }
}
package sujus.pickle.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "message", "Welcome to the admin dashboard",
                "userId", jwt.getClaim("userId"),
                "email", jwt.getSubject(),
                "role", jwt.getClaimAsString("role")
        );
    }
}
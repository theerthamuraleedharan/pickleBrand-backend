package sujus.pickle.admin;

import org.springframework.security.core.Authentication;
import sujus.pickle.common.CurrentUserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CurrentUserService currentUser;
    public AdminController(CurrentUserService currentUser) { this.currentUser = currentUser; }

    // Returns the basic overview data shown on the admin dashboard for the active user.
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(Authentication authentication) {
        var user = currentUser.get(authentication);
        return Map.of(
                "message", "Welcome to the admin dashboard",
                "userId", user.id(),
                "email", user.email(),
                "role", user.role()
        );
    }
}

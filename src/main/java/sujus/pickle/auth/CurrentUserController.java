package sujus.pickle.auth;

import org.springframework.security.core.Authentication;
import sujus.pickle.common.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CurrentUserController {

    private final CurrentUserService users;
    public CurrentUserController(CurrentUserService users) { this.users = users; }

    @GetMapping("/me")
    public CurrentUserService.Account getCurrentUser(
            Authentication authentication
    ) {
        // Return the LOCAL ID for cart/profile ownership. External sub is not a database ID.
        return users.get(authentication);
    }
}

package sujus.pickle.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.user.UserRepository;

@Service
public class CurrentUserService {
    // Resolves the local application user from the currently authenticated principal.
    private final UserRepository users;
    public CurrentUserService(UserRepository users) { this.users = users; }

    // Returns the visible user identity and role for the active request.
    // Reads the current authenticated user record and keeps the effective role from the active token.
    public Account get(Authentication authentication) {
        var user = users.findById(CurrentUser.id(authentication)).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again"));
        // The current authentication is authoritative for permissions; a linked legacy
        // ADMIN account must not grant admin rights to an OIDC CUSTOMER token.
        return new Account(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), CurrentUser.role(authentication));
    }

    public record Account(Long id, String firstName, String lastName, String email, String role) { }
}

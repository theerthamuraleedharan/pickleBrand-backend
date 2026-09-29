package sujus.pickle.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import sujus.pickle.security.oidc.OidcAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentUser {
    private CurrentUser() { }

    public static String role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")) ? "ADMIN" : "CUSTOMER";
    }

    public static Long id(Authentication authentication) {
        // OIDC identities are mapped by the server. An external userId claim is ignored.
        if (authentication instanceof OidcAuthenticationToken oidc) return oidc.getUserId();
        Object claim = authentication instanceof JwtAuthenticationToken local
                ? local.getToken().getClaim("userId") : null;
        if (!(claim instanceof Number number) || number.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again");
        }
        return number.longValue();
    }
}

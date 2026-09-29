package sujus.pickle.security.oidc;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Collection;

/** Keep verified JWT claims unchanged; local identity belongs in server-side authentication. */
public class OidcAuthenticationToken extends JwtAuthenticationToken {
    private final Long userId;
    public OidcAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities, Long userId) {
        super(jwt, authorities, jwt.getSubject());
        this.userId = userId;
    }
    public Long getUserId() { return userId; }
}

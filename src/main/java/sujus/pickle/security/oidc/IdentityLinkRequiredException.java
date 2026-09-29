package sujus.pickle.security.oidc;

import org.springframework.security.core.AuthenticationException;

/** A safe, actionable error; never silently attach an external login by email. */
public class IdentityLinkRequiredException extends AuthenticationException {
    public IdentityLinkRequiredException() {
        super("This sign-in needs account linking. Contact the store administrator to preserve your existing account.");
    }
}

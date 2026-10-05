package sujus.pickle.security.oidc;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sujus.pickle.user.*;
import java.util.Locale;
import java.util.Objects;

@Service
@Profile("oidc")
public class OidcIdentityService {
    // Maps an external OIDC subject to the local app user and handles first-login provisioning.
    private final ExternalIdentityRepository identities;
    private final UserRepository users;
    private final TransactionTemplate transactions;

    public OidcIdentityService(ExternalIdentityRepository identities, UserRepository users,
                               PlatformTransactionManager transactionManager) {
        this.identities = identities;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // Resolves the local user tied to this external OIDC identity and creates a first-time user if needed.
    public Long resolveUserId(Jwt jwt) {
        String issuer = jwt.getClaimAsString("iss");
        String subject = jwt.getSubject();
        try {
            return Objects.requireNonNull(transactions.execute(status -> resolveOrCreate(jwt, issuer, subject)));
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Two requests may both see a first login. Uniqueness is enforced in PostgreSQL.
            // Read AFTER the failed transaction rolls back, not inside a rollback-only transaction.
            return identities.findByIssuerAndSubject(issuer, subject)
                    .map(ExternalIdentity::getUserId).orElseThrow(IdentityLinkRequiredException::new);
        }
    }

    // Finds a linked OIDC account or creates a brand-new app user if the identity is first seen.
    private Long resolveOrCreate(Jwt jwt, String issuer, String subject) {
        var existing = identities.findByIssuerAndSubject(issuer, subject);
        if (existing.isPresent()) return existing.get().getUserId();

        // Verified email is contact information for NEW accounts, not proof of ownership
        // of an existing local account. Existing users require an explicit operator link.
        String email = jwt.getClaimAsString("email");
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified")) || email == null
                || email.isBlank() || email.length() > 255 || !email.contains("@")) {
            throw new InvalidBearerTokenException("A verified email is required for a new account");
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new IdentityLinkRequiredException();

        // Never promote a local account from a token. OIDC permissions are evaluated
        // separately from the signed API-client roles on every request.
        AppUser user = users.saveAndFlush(new AppUser(name(jwt, "given_name"), name(jwt, "family_name"),
                email, null, Role.CUSTOMER));
        identities.saveAndFlush(new ExternalIdentity(user.getId(), issuer, subject));
        return user.getId();
    }

    // Extracts a single claim value safely and keeps the user's display name within app limits.
    private String name(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        if (value == null) return "";
        value = value.trim();
        return value.substring(0, Math.min(value.length(), 100));
    }
}

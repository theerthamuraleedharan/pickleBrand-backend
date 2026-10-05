package sujus.pickle.security.oidc;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, Long> {
    Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject);
}

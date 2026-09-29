package sujus.pickle.security.oidc;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Identity is (issuer, subject), never an email address or a browser-provided user ID. */
@Entity
@Table(name = "external_identities")
public class ExternalIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 512)
    private String issuer;
    @Column(nullable = false, length = 255)
    private String subject;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ExternalIdentity() { }
    public ExternalIdentity(Long userId, String issuer, String subject) {
        this.userId = userId;
        this.issuer = issuer;
        this.subject = subject;
        this.createdAt = OffsetDateTime.now();
    }
    public Long getUserId() { return userId; }
}

package sujus.pickle.cart;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "cart_imports")
public class CartImport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "migration_id", nullable = false)
    private UUID migrationId;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    protected CartImport() { }
    public CartImport(Long userId, UUID migrationId, String requestHash) {
        this.userId = userId;
        this.migrationId = migrationId;
        this.requestHash = requestHash;
    }
    public String getRequestHash() { return requestHash; }
}

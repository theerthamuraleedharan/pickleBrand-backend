package sujus.pickle.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CartImportRepository extends JpaRepository<CartImport, Long> {
    Optional<CartImport> findByUserIdAndMigrationId(Long userId, UUID migrationId);
}

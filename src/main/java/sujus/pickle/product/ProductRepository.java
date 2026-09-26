package sujus.pickle.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdateById(@org.springframework.data.repository.query.Param("id") Long id);

    List<Product> findAllByActiveTrueOrderByCreatedAtDesc();

    Optional<Product> findByIdAndActiveTrue(Long id);
}

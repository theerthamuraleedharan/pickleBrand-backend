package sujus.pickle.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByActiveTrueOrderByCreatedAtDesc();

    Optional<Product> findByIdAndActiveTrue(Long id);
}
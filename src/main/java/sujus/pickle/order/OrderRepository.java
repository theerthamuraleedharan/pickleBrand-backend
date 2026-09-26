package sujus.pickle.order;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByUserIdAndIdempotencyKey(Long userId, UUID key);
    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);
    Page<CustomerOrder> findAllByUserId(Long userId, Pageable pageable);
}

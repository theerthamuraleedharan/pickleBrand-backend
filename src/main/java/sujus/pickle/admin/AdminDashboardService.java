package sujus.pickle.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sujus.pickle.product.ProductRepository;
import sujus.pickle.user.Role;
import sujus.pickle.user.UserRepository;

@Service
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public AdminDashboardService(UserRepository userRepository, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        long totalCustomers = userRepository.countByRole(Role.CUSTOMER);

        long totalProducts = productRepository.count();

        return new AdminDashboardSummaryResponse(
                totalCustomers,
                totalProducts
        );
    }
}
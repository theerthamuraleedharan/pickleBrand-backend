package sujus.pickle.product;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    // Business logic for reading and creating active products in the storefront.
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Retrieves every active product for the storefront listing.
    @Transactional(readOnly = true)
    public List<ProductResponse> getActiveProducts() {
        return productRepository
                .findAllByActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    // Reads one active product by ID and returns a 404 when it is missing or inactive.
    @Transactional(readOnly = true)
    public ProductResponse getActiveProduct(Long productId) {
        Product product = productRepository
                .findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        return ProductResponse.from(product);
    }

    // Creates a new product after trimming inputs and preserving the default active state.
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product(
                request.name().trim(),
                request.description().trim(),
                request.price(),
                request.stockQuantity(),
                request.weightGrams(),
                request.spiceLevel(),
                normalizeUrl(request.imageUrl()),
                request.active() == null || request.active(),
                request.category()
        );

        return ProductResponse.from(productRepository.save(product));
    }

    private String normalizeUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        return imageUrl.trim();
    }
}
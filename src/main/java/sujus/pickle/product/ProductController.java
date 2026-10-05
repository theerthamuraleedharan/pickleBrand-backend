package sujus.pickle.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "http://localhost:5173")
public class ProductController {

    // Product-related API endpoints live here so the controller stays focused on HTTP concerns.
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Returns all products that are currently active and available to customers.
    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.getActiveProducts();
    }

    // Fetches one active product by its ID for detailed product views.
    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable Long productId) {
        return productService.getActiveProduct(productId);
    }

    // Creates a new product after validating the incoming request body.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }
}
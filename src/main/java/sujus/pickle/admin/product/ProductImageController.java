package sujus.pickle.admin.product;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import sujus.pickle.product.Product;
import sujus.pickle.product.ProductRepository;

@RestController
@RequestMapping("/api/products")
public class ProductImageController {

    private final ProductRepository productRepository;
    private final ProductImageStorageService imageStorage;

    public ProductImageController(
            ProductRepository productRepository,
            ProductImageStorageService imageStorage
    ) {
        this.productRepository =
                productRepository;

        this.imageStorage =
                imageStorage;
    }

    // Serves the product image stored on disk for the storefront or admin UI.
    @GetMapping("/{productId}/photo")
    public ResponseEntity<Resource> getProductPhoto(
            @PathVariable Long productId
    ) {

        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow();

        if (product.getImageName() == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        ProductImageStorageService.StoredImage image =
                imageStorage.load(
                        product.getImageName()
                );

        return ResponseEntity
                .ok()
                .contentType(
                        image.mediaType()
                )
                .cacheControl(
                        CacheControl.noCache()
                )
                .body(
                        image.resource()
                );
    }
}

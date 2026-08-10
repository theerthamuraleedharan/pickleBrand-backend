package sujus.pickle.product;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import sujus.pickle.admin.product.ProductImageStorageService;

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
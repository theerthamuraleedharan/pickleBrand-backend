package sujus.pickle.admin.product;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductService service;

    public AdminProductController(
            AdminProductService service
    ) {
        this.service = service;
    }

    // Lists every catalog product for the admin dashboard and product management screens.
    @GetMapping
    public List<AdminProductResponse>
    getProducts() {

        return service.getProducts();
    }

    // Reads one product record for admin editing or inspection.
    @GetMapping("/{productId}")
    public AdminProductResponse getProduct(
            @PathVariable Long productId
    ) {
        return service.getProduct(
                productId
        );
    }

    // Creates a catalog item and optionally uploads a product image in the same multipart request.
    @PostMapping(
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse createProduct(

            @Valid
            @RequestPart("product")
            AdminProductRequest request,

            @RequestPart(
                    value = "photo",
                    required = false
            )
            MultipartFile photo
    ) {
        return service.createProduct(
                request,
                photo
        );
    }

    // Updates a product's details and replaces the image if a new photo is supplied.
    @PutMapping(
            value = "/{productId}",
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AdminProductResponse updateProduct(

            @PathVariable Long productId,

            @Valid
            @RequestPart("product")
            AdminProductRequest request,

            @RequestPart(
                    value = "photo",
                    required = false
            )
            MultipartFile photo
    ) {
        return service.updateProduct(
                productId,
                request,
                photo
        );
    }

    // Deletes a product and its related image from the catalog.
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable Long productId
    ) {
        service.deleteProduct(
                productId
        );
    }
}
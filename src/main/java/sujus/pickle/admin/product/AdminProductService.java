package sujus.pickle.admin.product;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.product.*;

import java.util.List;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductImageStorageService imageStorage;

    public AdminProductService(
            ProductRepository productRepository,
            ProductImageStorageService imageStorage
    ) {
        this.productRepository =
                productRepository;

        this.imageStorage =
                imageStorage;
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse>
    getProducts() {

        return productRepository
                .findAll()
                .stream()
                .map(
                        AdminProductResponse::from
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminProductResponse getProduct(
            Long productId
    ) {
        return AdminProductResponse.from(
                findProduct(productId)
        );
    }

    @Transactional
    public AdminProductResponse createProduct(
            AdminProductRequest request,
            MultipartFile photo
    ) {

        Product product = new Product(
                request.name().trim(),
                request.description().trim(),
                request.price(),
                request.stockQuantity(),
                request.weightGrams(),
                request.spiceLevel(),
                request.category(),
                request.active() == null
                        || request.active()
        );

        String newImageName = null;

        try {
            if (photo != null
                    && !photo.isEmpty()) {

                newImageName =
                        imageStorage.store(photo);

                product.updateImageName(
                        newImageName
                );
            }

            Product saved =
                    productRepository
                            .saveAndFlush(product);

            return AdminProductResponse.from(
                    saved
            );

        } catch (RuntimeException exception) {

            if (newImageName != null) {
                imageStorage.delete(
                        newImageName
                );
            }

            throw exception;
        }
    }

    @Transactional
    public AdminProductResponse updateProduct(
            Long productId,
            AdminProductRequest request,
            MultipartFile photo
    ) {

        Product product = findProductForUpdate(productId);

        String previousImage = product.getImageName();

        String newImage = null;

        product.update(
                request.name().trim(),
                request.description().trim(),
                request.price(),
                request.stockQuantity(),
                request.weightGrams(),
                request.spiceLevel(),
                request.category(),
                request.active() == null
                        || request.active()
        );

        try {
            if (photo != null
                    && !photo.isEmpty()) {

                newImage =
                        imageStorage.store(photo);

                product.updateImageName(
                        newImage
                );
            }

            productRepository.saveAndFlush(
                    product
            );

            if (newImage != null
                    && previousImage != null) {

                imageStorage.delete(
                        previousImage
                );
            }

            return AdminProductResponse.from(
                    product
            );

        } catch (RuntimeException exception) {

            if (newImage != null) {
                imageStorage.delete(
                        newImage
                );
            }

            throw exception;
        }
    }

    @Transactional
    public void deleteProduct(
            Long productId
    ) {
        Product product =
                findProductForUpdate(productId);

        String imageName =
                product.getImageName();

        productRepository.delete(product);
        productRepository.flush();

        imageStorage.delete(imageName);
    }

    private Product findProduct(
            Long productId
    ) {
        return productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product was not found"
                        )
                );
    }

    private Product findProductForUpdate(Long productId) {
        return productRepository.findForUpdateById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found"));
    }
}

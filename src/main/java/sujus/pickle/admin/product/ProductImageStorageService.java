package sujus.pickle.admin.product;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductImageStorageService {

    // Stores catalog images with validation for size and supported formats.
    private static final long MAX_SIZE =
            5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png"
            );

    private final Path rootDirectory;

    public ProductImageStorageService(
            @Value("${app.storage.product-images}")
            String directory
    ) {
        try {
            rootDirectory = Paths
                    .get(directory)
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(
                    rootDirectory
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create product image directory",
                    exception
            );
        }
    }

    public String store(MultipartFile photo) {
        validate(photo);

        String extension = switch (
                photo.getContentType()
                ) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported image type"
                    );
        };

        String filename =
                UUID.randomUUID() + extension;

        Path destination = rootDirectory
                .resolve(filename)
                .normalize();

        if (!destination.startsWith(
                rootDirectory
        )) {
            throw new IllegalArgumentException(
                    "Invalid file path"
            );
        }

        try {
            Files.copy(
                    photo.getInputStream(),
                    destination,
                    StandardCopyOption
                            .REPLACE_EXISTING
            );

            return filename;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not save product image",
                    exception
            );
        }
    }

    public StoredImage load(
            String filename
    ) {
        try {
            Path path = rootDirectory
                    .resolve(filename)
                    .normalize();

            if (!path.startsWith(rootDirectory)) {
                throw new IllegalArgumentException(
                        "Invalid image path"
                );
            }

            Resource resource =
                    new UrlResource(
                            path.toUri()
                    );

            if (!resource.exists()
                    || !resource.isReadable()) {

                throw new IllegalArgumentException(
                        "Product image was not found"
                );
            }

            String contentType =
                    Files.probeContentType(path);

            MediaType mediaType =
                    contentType == null
                            ? MediaType.APPLICATION_OCTET_STREAM
                            : MediaType.parseMediaType(
                            contentType
                    );

            return new StoredImage(
                    resource,
                    mediaType
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read product image",
                    exception
            );
        }
    }

    public void delete(String filename) {
        if (filename == null) {
            return;
        }

        try {
            Path path = rootDirectory
                    .resolve(filename)
                    .normalize();

            if (path.startsWith(rootDirectory)) {
                Files.deleteIfExists(path);
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not delete product image",
                    exception
            );
        }
    }

    private void validate(
            MultipartFile photo
    ) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException(
                    "Select an image"
            );
        }

        if (photo.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Product image cannot exceed 5 MB"
            );
        }

        if (!ALLOWED_TYPES.contains(
                photo.getContentType()
        )) {
            throw new IllegalArgumentException(
                    "Only JPEG and PNG images are allowed"
            );
        }

        try {
            if (ImageIO.read(
                    photo.getInputStream()
            ) == null) {

                throw new IllegalArgumentException(
                        "The uploaded file is not a valid image"
                );
            }

        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "The uploaded image could not be read"
            );
        }
    }

    public record StoredImage(
            Resource resource,
            MediaType mediaType
    ) {
    }
}
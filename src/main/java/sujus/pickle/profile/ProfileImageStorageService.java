package sujus.pickle.profile;

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
public class ProfileImageStorageService {

    private static final long MAX_FILE_SIZE =
            2L * 1024L * 1024L;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/jpg");

    private final Path rootDirectory;

    public ProfileImageStorageService(@Value("${app.storage.profile-images}") String directory) {
        try {
            rootDirectory = Paths
                    .get(directory)
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create profile image directory",
                    exception
            );
        }
    }

    public String store(MultipartFile file) {
        validate(file);

        String extension = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> throw new IllegalArgumentException(
                    "Only PNG and JPEG images are allowed"
            );
        };

        String generatedName = UUID.randomUUID() + extension;

        Path destination = rootDirectory
                .resolve(generatedName)
                .normalize();

        if (!destination.startsWith(rootDirectory)) {
            throw new IllegalArgumentException(
                    "Invalid file path"
            );
        }

        try {
            Files.copy(
                    file.getInputStream(),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return generatedName;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not save profile image",
                    exception
            );
        }
    }

    public StoredImage load(String filename) {
        try {
            Path filePath = rootDirectory
                    .resolve(filename)
                    .normalize();

            if (!filePath.startsWith(rootDirectory)) {
                throw new IllegalArgumentException(
                        "Invalid file path"
                );
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()
                    || !resource.isReadable()) {
                throw new IllegalArgumentException(
                        "Profile image was not found"
                );
            }

            String detectedType =
                    Files.probeContentType(filePath);

            MediaType mediaType =
                    detectedType == null
                            ? MediaType.APPLICATION_OCTET_STREAM
                            : MediaType.parseMediaType(
                            detectedType
                    );

            return new StoredImage(
                    resource,
                    mediaType
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read profile image",
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
                    "Could not delete profile image",
                    exception
            );
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Select an image to upload"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Profile image cannot exceed 2 MB"
            );
        }

        if (!ALLOWED_TYPES.contains(
                file.getContentType()
        )) {
            throw new IllegalArgumentException(
                    "Only PNG and JPEG images are allowed"
            );
        }

        try {
            if (ImageIO.read(file.getInputStream()) == null) {
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
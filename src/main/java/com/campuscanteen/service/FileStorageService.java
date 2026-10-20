package com.campuscanteen.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.campuscanteen.config.AppProperties;

/**
 * Saves uploaded images (payment screenshots, the payment QR code and food photos) to a folder on disk.
 * MongoDB stores only the file name, not the image itself.
 *
 * Safety rules:
 * - Only JPEG and PNG images up to 5 MB are accepted. The type is checked from the file's
 *   actual content, not from its name.
 * - Files get a random name, so users cannot overwrite or guess other files.
 * - The folder is not publicly served; images are only sent through controllers that check permissions.
 */
@Service
public class FileStorageService {

    public static final String PAYMENT_PROOFS = "payment-proofs";
    public static final String QR = "qr";
    public static final String MENU_PHOTOS = "menu-photos";

    /** Largest accepted image (5 MB). Also used by UploadSizeFilter and the upload forms. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png)$");

    private final Path baseDir;

    public FileStorageService(AppProperties properties) {
        this.baseDir = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    /** Checks the upload and returns its extension ("jpg" or "png"). Throws BusinessException if unacceptable. */
    public String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Please choose an image file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("The image is too large. The maximum size is 5 MB.");
        }
        byte[] header = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new BusinessException("The file could not be read. Please try again.");
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "png";
        }
        throw new BusinessException("Only JPEG or PNG images are accepted.");
    }

    /** Validates and saves the image; returns the generated file name. */
    public String store(MultipartFile file, String folder) {
        String extension = validateImage(file);
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Path dir = Files.createDirectories(baseDir.resolve(folder));
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save uploaded file", e);
        }
        return fileName;
    }

    public Resource load(String folder, String fileName) {
        Path path = resolve(folder, fileName);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("File not found");
        }
        return new PathResource(path);
    }

    public void delete(String folder, String fileName) {
        try {
            Files.deleteIfExists(resolve(folder, fileName));
        } catch (IOException ignored) {
            // A leftover file does no harm.
        }
    }

    public static MediaType mediaTypeOf(String fileName) {
        return fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    }

    private Path resolve(String folder, String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            throw new NotFoundException("File not found");
        }
        return baseDir.resolve(folder).resolve(fileName);
    }
}

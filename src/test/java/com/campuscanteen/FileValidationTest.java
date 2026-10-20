package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.campuscanteen.config.AppProperties;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.FileStorageService;

/** Payment screenshots: only real JPEG/PNG images up to 5 MB are accepted. No database needed. */
class FileValidationTest {

    private final FileStorageService storage =
            new FileStorageService(new AppProperties("Asia/Kolkata", "target/test-uploads", null));

    @Test
    void acceptsPngAndJpeg() {
        assertEquals("png", storage.validateImage(TestImages.png("proof.png")));
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        assertEquals("jpg", storage.validateImage(new MockMultipartFile("f", "a.jpg", "image/jpeg", jpeg)));
    }

    @Test
    void rejectsNonImageEvenWithImageName() {
        MockMultipartFile fake = new MockMultipartFile("f", "proof.png", "image/png", "<html>not an image".getBytes());
        assertThrows(BusinessException.class, () -> storage.validateImage(fake));
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThrows(BusinessException.class,
                () -> storage.validateImage(new MockMultipartFile("f", "a.png", "image/png", new byte[0])));
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(TestImages.PNG_BYTES, 0, big, 0, TestImages.PNG_BYTES.length);
        assertThrows(BusinessException.class,
                () -> storage.validateImage(new MockMultipartFile("f", "a.png", "image/png", big)));
    }
}

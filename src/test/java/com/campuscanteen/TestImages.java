package com.campuscanteen;

import org.springframework.mock.web.MockMultipartFile;

/** A tiny file that starts with the PNG signature, used as a fake payment screenshot in tests. */
final class TestImages {

    static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    private TestImages() {
    }

    static MockMultipartFile png(String name) {
        return new MockMultipartFile("paymentProof", name, "image/png", PNG_BYTES);
    }
}

package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Uploads real files to a real running web server (not a simulated one), because the upload
 * size limits are enforced by the Tomcat server itself.
 *
 * Checks that a too-large image produces the friendly "too large" message instead of a
 * "403 Forbidden" error (requirement: oversized files must not look like an authorization error).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.mongodb.database=campus_canteen_test",
        "app.upload-dir=target/test-uploads",
        "app.admin-setup.college-id="
})
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class RealServerUploadTest extends IntegrationTestBase {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @LocalServerPort int port;

    private HttpClient http;

    @BeforeEach
    void logInAsAdministrator() throws Exception {
        admin("UPLOADADMIN");
        http = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String token = csrfFrom(get("/admin/login").body());
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(url("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("collegeId=UPLOADADMIN&password=adminpass123&source=admin&_csrf="
                        + URLEncoder.encode(token, StandardCharsets.UTF_8)))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, login.statusCode());
        assertTrue(login.headers().firstValue("Location").orElse("").endsWith("/admin"), "admin login should succeed");
    }

    @Test
    void imageAboveServerLimitShowsSizeMessageNotForbidden() throws Exception {
        assertUploadShowsTooLarge(12 * 1024 * 1024);   // above the 10 MB server limit
    }

    @Test
    void imageAboveApplicationLimitShowsSizeMessage() throws Exception {
        assertUploadShowsTooLarge(6 * 1024 * 1024);    // above the 5 MB application limit
    }

    @Test
    void smallImageIsAccepted() throws Exception {
        String formAction = uploadFormAction(get("/admin/settings").body());
        HttpResponse<String> response = upload(formAction, 1024);
        assertEquals(302, response.statusCode());
        assertTrue(get("/admin/settings").body().contains("Payment QR code updated"));
        assertTrue(canteenService.getDefaultCanteen().isPaymentQrConfigured());
    }

    private void assertUploadShowsTooLarge(int bytes) throws Exception {
        String formAction = uploadFormAction(get("/admin/settings").body());
        HttpResponse<String> response = upload(formAction, bytes);
        assertEquals(302, response.statusCode(), "expected a redirect back to the form, got " + response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElse("").endsWith("/admin/settings"));
        assertTrue(get("/admin/settings").body().contains("The image is too large. The maximum size is 5 MB."));
    }

    // ---------- helpers ----------

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(url(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfFrom(String html) {
        Matcher m = CSRF.matcher(html);
        assertTrue(m.find(), "page has no CSRF token");
        return m.group(1);
    }

    /** The action attribute of the QR upload form, exactly as the browser would use it. */
    private static String uploadFormAction(String html) {
        Matcher m = Pattern.compile("action=\"(/admin/settings/qr[^\"]*)\"").matcher(html);
        assertTrue(m.find(), "QR upload form not found");
        String action = m.group(1).replace("&amp;", "&");
        // Like a browser, the hidden _csrf field is also sent inside the form body.
        return action + "\n" + csrfFrom(html.substring(m.start()));
    }

    /** Sends a multipart upload the way a browser does: hidden _csrf field first, then the file. */
    private HttpResponse<String> upload(String formAction, int bytes) throws Exception {
        String[] parts = formAction.split("\n");
        String boundary = "----TestBoundary" + System.nanoTime();
        byte[] image = new byte[bytes];
        System.arraycopy(TestImages.PNG_BYTES, 0, image, 0, TestImages.PNG_BYTES.length);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + parts[1] + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"qrImage\"; filename=\"qr.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(image);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(url(parts[0]))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}

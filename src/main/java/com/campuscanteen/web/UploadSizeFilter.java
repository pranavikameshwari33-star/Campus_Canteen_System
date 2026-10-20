package com.campuscanteen.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import com.campuscanteen.service.FileStorageService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stops oversized uploads before anything else looks at them.
 *
 * Why this is needed: an upload form sends its security (CSRF) token inside the same body as the
 * image. If the body is larger than the server allows, the server cannot read the token either,
 * and the user would see a confusing "Forbidden" or "413" error page.
 *
 * This filter runs before Spring Security. It reads only the size the browser declares
 * (Content-Length) and, if it is too large, sends the user back to the form with the normal
 * "image is too large" message. Nothing is saved and nothing is changed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UploadSizeFilter extends OncePerRequestFilter {

    public static final String TOO_LARGE_MESSAGE = "The image is too large. The maximum size is 5 MB.";

    /** The image limit plus some room for the other form fields. */
    private static final long MAX_REQUEST_BYTES = FileStorageService.MAX_BYTES + 64 * 1024;

    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        return !"POST".equals(request.getMethod())
                || contentType == null || !contentType.toLowerCase().startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() <= MAX_REQUEST_BYTES) {
            chain.doFilter(request, response);
            return;
        }
        String target = formPageFor(request.getRequestURI().substring(request.getContextPath().length()));
        FlashMap flash = new FlashMap();
        flash.put("error", TOO_LARGE_MESSAGE);
        flash.setTargetRequestPath(request.getContextPath() + target);
        flashMapManager.saveOutputFlashMap(flash, request, response);
        response.sendRedirect(request.getContextPath() + target);
    }

    /**
     * The page that holds the upload form: the food item list for food photos, QR settings for other
     * administrator uploads, the QR payment page for customers.
     */
    static String formPageFor(String path) {
        if (path.startsWith("/admin/menu-items")) {
            return "/admin/menu-items";
        }
        if (path.startsWith("/admin")) {
            return "/admin/settings";
        }
        if (path.matches("/orders/[A-Za-z0-9]+/payment-proof")) {
            return path.replace("/payment-proof", "/pay");
        }
        return "/orders";
    }
}

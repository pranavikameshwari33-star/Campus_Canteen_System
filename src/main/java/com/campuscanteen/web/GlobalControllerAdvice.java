package com.campuscanteen.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.NotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Applies to every page:
 * - makes the logged-in user available to templates as "currentUser" (for the navigation bar);
 * - turns "not found" into a plain 404 page;
 * - turns an oversized upload into a friendly message;
 * - shows a clear "database unavailable" page (HTTP 503) if MongoDB cannot be reached.
 * Unexpected errors are shown by Spring Boot's error pages (templates/error/*.html) without technical details.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    @ModelAttribute("currentUser")
    public CanteenUserDetails currentUser(@AuthenticationPrincipal CanteenUserDetails user) {
        return user;
    }

    /** Used by the navigation bar to highlight the current page. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Model model, @AuthenticationPrincipal CanteenUserDetails user) {
        model.addAttribute("currentUser", user);
        return "error/404";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", UploadSizeFilter.TOO_LARGE_MESSAGE);
        return "redirect:" + UploadSizeFilter.formPageFor(request.getRequestURI());
    }

    /**
     * MongoDB could not be reached (for example the internet connection dropped or Atlas is down).
     * The user is told plainly instead of seeing a page that pretends everything worked.
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String databaseUnavailable(DataAccessResourceFailureException e, Model model,
                                      @AuthenticationPrincipal CanteenUserDetails user) {
        log.warn("Database unavailable: {}", e.getClass().getSimpleName());
        model.addAttribute("currentUser", user);
        return "error/database";
    }
}

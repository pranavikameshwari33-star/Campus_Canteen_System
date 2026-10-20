package com.campuscanteen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.AdminRegistrationService;
import com.campuscanteen.service.AdminRegistrationService.NewInvite;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.web.form.AdminRegistrationForm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Administrator registration (/admin/register), invitation codes, and the /admin/dashboard address.
 * The rules for who may register are in AdminRegistrationService.
 */
@Controller
public class AdminRegistrationController {

    private final AdminRegistrationService adminRegistrationService;

    public AdminRegistrationController(AdminRegistrationService adminRegistrationService) {
        this.adminRegistrationService = adminRegistrationService;
    }

    /** The dashboard lives at /admin; /admin/dashboard is accepted too. */
    @GetMapping("/admin/dashboard")
    public String dashboard() {
        return "redirect:/admin";
    }

    @GetMapping("/admin/register")
    public String registerForm(@AuthenticationPrincipal CanteenUserDetails user, Model model, HttpServletRequest request) {
        if (user != null) {
            return "redirect:/";
        }
        model.addAttribute("form", new AdminRegistrationForm());
        addSetupInfo(model, request);
        return "auth/admin-register";
    }

    @PostMapping("/admin/register")
    public String register(@AuthenticationPrincipal CanteenUserDetails user,
                           @Valid @ModelAttribute("form") AdminRegistrationForm form, BindingResult result,
                           Model model, RedirectAttributes redirect, HttpServletRequest request) {
        if (user != null) {
            return "redirect:/";
        }
        addSetupInfo(model, request);
        if (result.hasErrors()) {
            return "auth/admin-register";
        }
        try {
            adminRegistrationService.register(form, request.getRemoteAddr());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/admin-register";
        }
        redirect.addFlashAttribute("success", "Administrator account created. You can now log in.");
        return "redirect:/admin/login";
    }

    /** An existing administrator creates a one-time code for a new administrator (Accounts page). */
    @PostMapping("/admin/accounts/invite")
    public String invite(@AuthenticationPrincipal CanteenUserDetails admin, RedirectAttributes redirect) {
        NewInvite invite = adminRegistrationService.createInvite(admin.getCollegeId());
        redirect.addFlashAttribute("newInvite", invite);
        return "redirect:/admin/accounts";
    }

    private void addSetupInfo(Model model, HttpServletRequest request) {
        model.addAttribute("firstAdmin", adminRegistrationService.isFirstAdminSetupOpen());
        model.addAttribute("setupCodeRequired", adminRegistrationService.isSetupCodeRequired());
        model.addAttribute("localInvitationCode", adminRegistrationService.isLocalInvitationCodeEnabled(request.getRemoteAddr()));
    }
}

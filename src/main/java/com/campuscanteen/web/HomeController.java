package com.campuscanteen.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.campuscanteen.model.Role;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.CurrentMenuService;
import com.campuscanteen.service.UserService;
import com.campuscanteen.web.form.RegistrationForm;

import jakarta.validation.Valid;

/** Public pages: welcome page, login pages and registration. */
@Controller
public class HomeController {

    private final UserService userService;
    private final CurrentMenuService currentMenuService;

    public HomeController(UserService userService, CurrentMenuService currentMenuService) {
        this.userService = userService;
        this.currentMenuService = currentMenuService;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CanteenUserDetails user, Model model) {
        if (user != null) {
            return user.isAdmin() ? "redirect:/admin" : "redirect:/home";
        }
        model.addAttribute("current", currentMenuService.current());
        return "index";
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal CanteenUserDetails user) {
        if (user != null) {
            return "redirect:/";
        }
        return "auth/login";
    }

    @GetMapping("/admin/login")
    public String adminLogin(@AuthenticationPrincipal CanteenUserDetails user) {
        if (user != null) {
            return "redirect:/";
        }
        return "auth/admin-login";
    }

    @GetMapping("/register")
    public String registerForm(Model model, @AuthenticationPrincipal CanteenUserDetails user) {
        if (user != null) {
            return "redirect:/";
        }
        RegistrationForm form = new RegistrationForm();
        form.setRole(Role.STUDENT);
        model.addAttribute("form", form);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult result,
                           Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerCustomer(form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
        redirect.addFlashAttribute("success", "Registration successful. You can now log in with your college ID.");
        return "redirect:/login";
    }
}

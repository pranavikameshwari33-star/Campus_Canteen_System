package com.campuscanteen.web;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.campuscanteen.model.User;
import com.campuscanteen.service.AdminRegistrationService;
import com.campuscanteen.service.UserService;

/**
 * Administrator page listing administrators (with invitation codes for new administrators) and
 * student and faculty accounts, where an account can be disabled
 * (for example if it was registered with someone else's roll number) or enabled again.
 */
@Controller
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    private final UserService userService;
    private final AdminRegistrationService adminRegistrationService;
    private final Clock clock;

    public AdminAccountController(UserService userService, AdminRegistrationService adminRegistrationService, Clock clock) {
        this.clock = clock;
        this.userService = userService;
        this.adminRegistrationService = adminRegistrationService;
    }

    @GetMapping
    public String accounts(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("accounts", userService.listCustomers(q));
        model.addAttribute("admins", adminRegistrationService.listAdmins());
        model.addAttribute("invites", adminRegistrationService.listInvites());
        model.addAttribute("now", Instant.now(clock));
        return "admin/accounts";
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable String id, @RequestParam boolean active,
                            @RequestParam(required = false) String q, RedirectAttributes redirect) {
        User user = userService.setCustomerActive(id, active);
        redirect.addFlashAttribute("success", "Account " + user.getCollegeId()
                + (active ? " enabled. The person can log in again." : " disabled. The person can no longer log in or order."));
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/accounts")
                .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                .encode().toUriString();
    }
}

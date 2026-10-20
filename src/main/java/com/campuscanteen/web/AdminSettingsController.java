package com.campuscanteen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.MealType;
import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.FileStorageService;
import com.campuscanteen.service.MealSessionService;
import com.campuscanteen.web.form.MealSessionForm;

import jakarta.validation.Valid;

/** Administrator pages for meal timings and canteen settings (including the payment QR code). */
@Controller
@RequestMapping("/admin")
public class AdminSettingsController {

    private static final String TIME_PATTERN = "^([01][0-9]|2[0-3]):[0-5][0-9]$";

    private final MealSessionService mealSessionService;
    private final CanteenService canteenService;
    private final FileStorageService fileStorage;

    public AdminSettingsController(MealSessionService mealSessionService, CanteenService canteenService,
                                   FileStorageService fileStorage) {
        this.mealSessionService = mealSessionService;
        this.canteenService = canteenService;
        this.fileStorage = fileStorage;
    }

    // ---------- Meal sessions ----------

    @GetMapping("/sessions")
    public String sessions(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MealSessionForm());
        }
        model.addAttribute("sessions", mealSessionService.listForCanteen(canteenService.getDefaultCanteen().getId()));
        model.addAttribute("canteen", canteenService.getDefaultCanteen());
        model.addAttribute("types", MealType.values());
        model.addAttribute("categories", MenuItem.CATEGORIES);
        return "admin/sessions";
    }

    @GetMapping("/sessions/{id}/edit")
    public String editSession(@PathVariable String id, Model model) {
        model.addAttribute("form", mealSessionService.toForm(mealSessionService.get(id)));
        return sessions(model);
    }

    @PostMapping("/sessions")
    public String saveSession(@Valid @ModelAttribute("form") MealSessionForm form, BindingResult result,
                              Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                MealSession saved = mealSessionService.save(canteenService.getDefaultCanteen().getId(), form);
                redirect.addFlashAttribute("success", "Saved " + saved.getName() + " (" + saved.getTimeRange() + ").");
                return "redirect:/admin/sessions";
            } catch (BusinessException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return sessions(model);
    }

    @PostMapping("/sessions/{id}/active")
    public String setSessionActive(@PathVariable String id, @RequestParam boolean active, RedirectAttributes redirect) {
        try {
            mealSessionService.setActive(id, active);
            redirect.addFlashAttribute("success", active ? "Session activated." : "Session deactivated.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sessions";
    }

    // ---------- Canteen settings and payment QR ----------

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("canteen", canteenService.getDefaultCanteen());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam String name, @RequestParam(required = false) String location,
                               @RequestParam String openingTime, @RequestParam String closingTime,
                               @RequestParam(required = false) String upiId, RedirectAttributes redirect) {
        if (name.isBlank() || name.length() > 80) {
            redirect.addFlashAttribute("error", "Enter a canteen name (80 characters maximum).");
        } else if (!openingTime.matches(TIME_PATTERN) || !closingTime.matches(TIME_PATTERN)) {
            redirect.addFlashAttribute("error", "Opening and closing times must look like 08:00.");
        } else if (openingTime.compareTo(closingTime) >= 0) {
            redirect.addFlashAttribute("error", "Closing time must be after opening time.");
        } else if (upiId != null && !upiId.isBlank() && !upiId.trim().matches("^[A-Za-z0-9._-]{2,64}@[A-Za-z0-9.-]{2,64}$")) {
            redirect.addFlashAttribute("error", "The UPI ID does not look valid (example: canteen@bank).");
        } else {
            Canteen canteen = canteenService.getDefaultCanteen();
            canteen.setName(name.trim());
            canteen.setLocation(location == null ? null : location.trim());
            canteen.setOpeningTime(openingTime);
            canteen.setClosingTime(closingTime);
            canteen.setUpiId(upiId == null || upiId.isBlank() ? null : upiId.trim());
            canteenService.save(canteen);
            redirect.addFlashAttribute("success", "Canteen details saved.");
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/qr")
    public String uploadQr(@RequestParam("qrImage") MultipartFile qrImage, RedirectAttributes redirect) {
        try {
            Canteen canteen = canteenService.getDefaultCanteen();
            String oldFile = canteen.getPaymentQrFile();
            canteen.setPaymentQrFile(fileStorage.store(qrImage, FileStorageService.QR));
            canteenService.save(canteen);
            if (oldFile != null) {
                fileStorage.delete(FileStorageService.QR, oldFile);
            }
            redirect.addFlashAttribute("success", "Payment QR code updated. Customers will see it at checkout.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}

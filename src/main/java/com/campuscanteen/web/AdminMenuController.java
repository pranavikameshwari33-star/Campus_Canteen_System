package com.campuscanteen.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import org.springframework.web.util.UriComponentsBuilder;

import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.security.CanteenUserDetails;
import com.campuscanteen.service.BusinessException;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.DailyMenuService;
import com.campuscanteen.service.DailyMenuService.SaveAction;
import com.campuscanteen.service.FileStorageService;
import com.campuscanteen.service.FoodPhotos;
import com.campuscanteen.service.MealSessionService;
import com.campuscanteen.service.MenuItemService;
import com.campuscanteen.web.form.DailyMenuForm;
import com.campuscanteen.web.form.MenuItemForm;

import jakarta.validation.Valid;

/** Administrator pages for the permanent food catalogue and the daily menus. */
@Controller
@RequestMapping("/admin")
public class AdminMenuController {

    private final MenuItemService menuItemService;
    private final DailyMenuService dailyMenuService;
    private final MealSessionService mealSessionService;
    private final CanteenService canteenService;
    private final FileStorageService fileStorage;
    private final FoodPhotos photos;

    public AdminMenuController(MenuItemService menuItemService, DailyMenuService dailyMenuService,
                               MealSessionService mealSessionService, CanteenService canteenService,
                               FileStorageService fileStorage, FoodPhotos photos) {
        this.menuItemService = menuItemService;
        this.dailyMenuService = dailyMenuService;
        this.mealSessionService = mealSessionService;
        this.canteenService = canteenService;
        this.fileStorage = fileStorage;
        this.photos = photos;
    }

    // ---------- Permanent catalogue ----------

    @GetMapping("/menu-items")
    public String menuItems(@RequestParam(required = false) String category,
                            @RequestParam(required = false) String show, Model model) {
        List<MenuItem> all = menuItemService.listAll();
        List<MenuItem> items = all.stream()
                .filter(i -> category == null || category.isBlank() || category.equals(i.getCategory()))
                .filter(i -> !"drafts".equals(show) || i.isDraft())
                .toList();
        model.addAttribute("items", items);
        model.addAttribute("usedIds", menuItemService.usedIds());
        model.addAttribute("totalCount", all.size());
        model.addAttribute("draftCount", all.stream().filter(MenuItem::isDraft).count());
        model.addAttribute("categories", MenuItem.CATEGORIES);
        model.addAttribute("selectedCategory", category == null ? "" : category);
        model.addAttribute("show", show == null ? "" : show);
        return "admin/menu-items";
    }

    @GetMapping("/menu-items/new")
    public String newMenuItem(Model model) {
        model.addAttribute("form", new MenuItemForm());
        return itemForm(model, false);
    }

    @GetMapping("/menu-items/{id}/edit")
    public String editMenuItem(@PathVariable String id, Model model) {
        model.addAttribute("form", menuItemService.toForm(menuItemService.get(id)));
        return itemForm(model, menuItemService.isUsed(id));
    }

    private String itemForm(Model model, boolean used) {
        model.addAttribute("categories", MenuItem.CATEGORIES);
        model.addAttribute("subcategories", MenuItem.CHINESE_SUBCATEGORIES);
        model.addAttribute("used", used);
        model.addAttribute("bundledPhotos", photos.all());
        model.addAttribute("maxImageBytes", FileStorageService.MAX_BYTES);
        return "admin/menu-item-form";
    }

    @PostMapping("/menu-items")
    public String saveMenuItem(@Valid @ModelAttribute("form") MenuItemForm form, BindingResult result,
                               @RequestParam(value = "photo", required = false) MultipartFile photo,
                               Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            String uploaded = null;
            try {
                // An uploaded photo wins over a typed link, and a typed link wins over the photo list.
                if (photo != null && !photo.isEmpty()) {
                    uploaded = fileStorage.store(photo, FileStorageService.MENU_PHOTOS);
                    form.setImageUrl("/files/menu-photo/" + uploaded);
                } else if (form.getPhotoLink() != null && !form.getPhotoLink().isBlank()) {
                    form.setImageUrl(form.getPhotoLink());
                }
                MenuItem saved = menuItemService.save(form);
                redirect.addFlashAttribute("success", "Saved " + saved.getName() + ".");
                return "redirect:/admin/menu-items";
            } catch (BusinessException e) {
                if (uploaded != null) {
                    fileStorage.delete(FileStorageService.MENU_PHOTOS, uploaded);
                    form.setImageUrl(null);
                }
                model.addAttribute("error", e.getMessage());
            }
        }
        return itemForm(model, form.getId() != null && !form.getId().isBlank() && menuItemService.isUsed(form.getId()));
    }

    @PostMapping("/menu-items/{id}/delete")
    public String deleteMenuItem(@PathVariable String id, RedirectAttributes redirect) {
        try {
            MenuItem deleted = menuItemService.delete(id);
            redirect.addFlashAttribute("success", "Deleted " + deleted.getName() + ".");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/menu-items";
    }

    @PostMapping("/menu-items/{id}/active")
    public String setMenuItemActive(@PathVariable String id, @RequestParam boolean active, RedirectAttributes redirect) {
        menuItemService.setActive(id, active);
        redirect.addFlashAttribute("success", active ? "Item activated." : "Item deactivated. It will not appear on new menus.");
        return "redirect:/admin/menu-items";
    }

    // ---------- Daily menus ----------

    @GetMapping("/daily-menu")
    public String dailyMenu(@RequestParam(required = false) String date,
                            @RequestParam(required = false) String sessionId, Model model) {
        String canteenId = canteenService.getDefaultCanteen().getId();
        String selectedDate = date == null || date.isBlank() ? dailyMenuService.today() : date;
        List<MealSession> sessions = mealSessionService.listForCanteen(canteenId);
        model.addAttribute("date", selectedDate);
        model.addAttribute("today", dailyMenuService.today());
        model.addAttribute("sessions", sessions);
        model.addAttribute("menusForDate", dailyMenuService.listForDate(canteenId, selectedDate));
        if (sessionId != null && !sessionId.isBlank()) {
            MealSession session = mealSessionService.get(sessionId);
            model.addAttribute("selectedSession", session);
            model.addAttribute("form", dailyMenuService.buildForm(canteenId, selectedDate, sessionId));
            model.addAttribute("existing", dailyMenuService.find(canteenId, selectedDate, sessionId).orElse(null));
            model.addAttribute("pastDate", selectedDate.compareTo(dailyMenuService.today()) < 0);
        }
        return "admin/daily-menu";
    }

    @PostMapping("/daily-menu")
    public String saveDailyMenu(@ModelAttribute("form") DailyMenuForm form, @RequestParam String action,
                                @AuthenticationPrincipal CanteenUserDetails admin, RedirectAttributes redirect) {
        SaveAction saveAction = switch (action) {
            case "publish" -> SaveAction.PUBLISH;
            case "unpublish" -> SaveAction.UNPUBLISH;
            default -> SaveAction.SAVE;
        };
        try {
            dailyMenuService.save(canteenService.getDefaultCanteen().getId(), form, admin.getCollegeId(), saveAction);
            redirect.addFlashAttribute("success", switch (saveAction) {
                case PUBLISH -> "Menu saved and published. Customers can order from it during the session.";
                case UNPUBLISH -> "Menu unpublished. Customers can no longer order from it.";
                default -> "Menu saved.";
            });
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/daily-menu")
                .queryParam("date", form.getDate())
                .queryParam("sessionId", form.getMealSessionId())
                .encode().toUriString();
    }
}

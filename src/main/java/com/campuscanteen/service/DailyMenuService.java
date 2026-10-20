package com.campuscanteen.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.DailyMenuItem;
import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.repository.DailyMenuRepository;
import com.campuscanteen.web.form.DailyMenuForm;

/**
 * Daily menus: which catalogue items are sold on a given date and meal session, at what price
 * and in what quantity. Also handles stock: reserving quantities when an order is placed and
 * returning them when an order is rejected or cancelled.
 *
 * Changing a daily menu never changes existing orders, because orders keep their own copy of
 * names and prices.
 */
@Service
public class DailyMenuService {

    public enum SaveAction { SAVE, PUBLISH, UNPUBLISH }

    private static final BigDecimal MAX_PRICE = new BigDecimal("10000");
    private static final int MAX_QUANTITY = 10000;

    private final DailyMenuRepository repository;
    private final MenuItemService menuItemService;
    private final MealSessionService mealSessionService;
    private final MongoTemplate mongo;
    private final Clock clock;

    public DailyMenuService(DailyMenuRepository repository, MenuItemService menuItemService,
                            MealSessionService mealSessionService, MongoTemplate mongo, Clock clock) {
        this.repository = repository;
        this.menuItemService = menuItemService;
        this.mealSessionService = mealSessionService;
        this.mongo = mongo;
        this.clock = clock;
    }

    public String today() {
        return LocalDate.now(clock).toString();
    }

    public Optional<DailyMenu> find(String canteenId, String date, String mealSessionId) {
        return repository.findByCanteenIdAndDateAndMealSessionId(canteenId, date, mealSessionId);
    }

    public DailyMenu get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Daily menu not found"));
    }

    /**
     * The published menu customers can order from right now, if any. Items whose category the
     * session does not allow (for example Chinese at breakfast) are left out of the returned copy,
     * so they can never be shown or ordered, even if they were stored on the menu by mistake.
     */
    public Optional<DailyMenu> findPublishedMenu(String canteenId, MealSession session) {
        Optional<DailyMenu> menu = find(canteenId, today(), session.getId()).filter(DailyMenu::isPublished);
        menu.ifPresent(m -> m.getItems().removeIf(item -> !session.allows(item.getCategory())));
        return menu;
    }

    public List<DailyMenu> listForDate(String canteenId, String date) {
        return repository.findByCanteenIdAndDate(canteenId, date);
    }

    /**
     * Builds the editor rows: every active catalogue item whose category this session allows,
     * plus any item already on this menu.
     */
    public DailyMenuForm buildForm(String canteenId, String date, String mealSessionId) {
        DailyMenu existing = find(canteenId, date, mealSessionId).orElse(null);
        MealSession session = mealSessionService.get(mealSessionId);
        DailyMenuForm form = new DailyMenuForm();
        form.setDate(date);
        form.setMealSessionId(mealSessionId);
        for (MenuItem item : menuItemService.listAll()) {
            DailyMenuItem onMenu = existing == null ? null : existing.findItem(item.getId()).orElse(null);
            boolean allowed = session.allows(item.getCategory());
            if (onMenu == null && (!item.isActive() || !allowed)) {
                continue;
            }
            DailyMenuForm.Row row = new DailyMenuForm.Row();
            row.setMenuItemId(item.getId());
            row.setName(item.getName() + (item.isActive() ? "" : " (deactivated)"));
            row.setCategory(item.getCategory());
            row.setCategoryLabel(item.getCategoryLabel());
            if (!allowed) {
                row.setNote("Not allowed in " + session.getName() + ": untick it");
            } else if (item.isNameNeedsConfirmation()) {
                row.setNote("Name needs confirmation on the Food items page");
            } else if (item.getDefaultPrice() == null && onMenu == null) {
                row.setNote("No price yet: enter one");
            }
            if (onMenu != null) {
                row.setIncluded(true);
                row.setPrice(onMenu.getPriceForThatDay());
                row.setQuantity(onMenu.getAvailableQuantity());
                row.setOriginalQuantity(onMenu.getAvailableQuantity());
                row.setAvailable(onMenu.isAvailable());
            } else {
                row.setPrice(item.getDefaultPrice());
                row.setQuantity(0);
            }
            form.getRows().add(row);
        }
        return form;
    }

    /**
     * Saves the editor. New menus are inserted; existing menus are changed item by item with
     * small atomic updates, so the stock reductions made by customers at the same time are kept.
     */
    public DailyMenu save(String canteenId, DailyMenuForm form, String adminCollegeId, SaveAction action) {
        LocalDate date = parseDate(form.getDate());
        if (date.isBefore(LocalDate.now(clock))) {
            throw new BusinessException("Menus for past dates cannot be changed.");
        }
        MealSession session = mealSessionService.get(form.getMealSessionId());
        if (!session.getCanteenId().equals(canteenId)) {
            throw new NotFoundException("Meal session not found");
        }
        Map<String, MenuItem> catalogue = new HashMap<>();
        menuItemService.listAll().forEach(i -> catalogue.put(i.getId(), i));
        validateRows(form, catalogue, session);

        long includedCount = form.getRows().stream().filter(DailyMenuForm.Row::isIncluded).count();
        if (action == SaveAction.PUBLISH && includedCount == 0) {
            throw new BusinessException("Tick at least one item before publishing.");
        }

        Optional<DailyMenu> existing = find(canteenId, date.toString(), session.getId());
        if (existing.isEmpty()) {
            DailyMenu menu = new DailyMenu();
            menu.setCanteenId(canteenId);
            menu.setDate(date.toString());
            menu.setMealSessionId(session.getId());
            menu.setMealSessionName(session.getName());
            for (DailyMenuForm.Row row : form.getRows()) {
                if (row.isIncluded()) {
                    menu.getItems().add(newItem(row, catalogue.get(row.getMenuItemId())));
                }
            }
            applyPublishState(menu, action, adminCollegeId);
            menu.setUpdatedAt(Instant.now(clock));
            try {
                return repository.insert(menu);
            } catch (DuplicateKeyException e) {
                // Another administrator created this menu a moment ago; update it instead.
                existing = find(canteenId, date.toString(), session.getId());
            }
        }
        return updateExisting(existing.orElseThrow(), form, catalogue, session, adminCollegeId, action);
    }

    private DailyMenu updateExisting(DailyMenu menu, DailyMenuForm form, Map<String, MenuItem> catalogue,
                                     MealSession session, String adminCollegeId, SaveAction action) {
        for (DailyMenuForm.Row row : form.getRows()) {
            String itemId = row.getMenuItemId();
            boolean onMenu = menu.findItem(itemId).isPresent();
            MenuItem item = catalogue.get(itemId);
            if (row.isIncluded() && onMenu) {
                Update update = new Update()
                        .set("items.$.priceForThatDay", money(row.getPrice()))
                        .set("items.$.available", row.isAvailable())
                        .set("items.$.name", item.getName())
                        .set("items.$.description", item.getDescription())
                        .set("items.$.category", item.getCategory())
                        .set("items.$.subcategory", item.getSubcategory())
                        .set("items.$.imageUrl", item.getImageUrl());
                if (!row.getQuantity().equals(row.getOriginalQuantity())) {
                    update.set("items.$.availableQuantity", row.getQuantity());
                }
                mongo.updateFirst(new Query(Criteria.where("id").is(menu.getId()).and("items.menuItemId").is(itemId)),
                        update, DailyMenu.class);
            } else if (row.isIncluded()) {
                mongo.updateFirst(new Query(Criteria.where("id").is(menu.getId()).and("items.menuItemId").ne(itemId)),
                        new Update().push("items", newItem(row, item)), DailyMenu.class);
            } else if (onMenu) {
                mongo.updateFirst(new Query(Criteria.where("id").is(menu.getId())),
                        new Update().pull("items", new Document("menuItemId", itemId)), DailyMenu.class);
            }
        }
        Update update = new Update()
                .set("mealSessionName", session.getName())
                .set("updatedAt", Instant.now(clock));
        if (action == SaveAction.PUBLISH) {
            update.set("published", true).set("publishedBy", adminCollegeId).set("publishedAt", Instant.now(clock));
        } else if (action == SaveAction.UNPUBLISH) {
            update.set("published", false);
        }
        mongo.updateFirst(new Query(Criteria.where("id").is(menu.getId())), update, DailyMenu.class);
        return get(menu.getId());
    }

    private void applyPublishState(DailyMenu menu, SaveAction action, String adminCollegeId) {
        if (action == SaveAction.PUBLISH) {
            menu.setPublished(true);
            menu.setPublishedBy(adminCollegeId);
            menu.setPublishedAt(Instant.now(clock));
        } else if (action == SaveAction.UNPUBLISH) {
            menu.setPublished(false);
        }
    }

    private void validateRows(DailyMenuForm form, Map<String, MenuItem> catalogue, MealSession session) {
        for (DailyMenuForm.Row row : form.getRows()) {
            if (!catalogue.containsKey(row.getMenuItemId())) {
                throw new BusinessException("The menu contains an unknown item. Reload the page and try again.");
            }
            if (!row.isIncluded()) {
                continue;
            }
            MenuItem item = catalogue.get(row.getMenuItemId());
            String name = item.getName();
            if (!session.allows(item.getCategory())) {
                throw new BusinessException(name + " (" + item.getCategoryLabel() + ") cannot be sold during "
                        + session.getName() + ". Untick it.");
            }
            if (item.isNameNeedsConfirmation()) {
                throw new BusinessException("The name of " + name + " still needs to be confirmed. Check it on the "
                        + "Food items page (Edit) before selling it.");
            }
            if (row.getPrice() == null) {
                throw new BusinessException("Enter a price for " + name + ".");
            }
            if (row.getPrice().signum() <= 0 || row.getPrice().compareTo(MAX_PRICE) > 0 || row.getPrice().scale() > 2) {
                throw new BusinessException("Enter a valid price for " + name + " (more than ₹0, at most 2 decimal places).");
            }
            if (row.getQuantity() == null || row.getQuantity() < 0 || row.getQuantity() > MAX_QUANTITY) {
                throw new BusinessException("Enter the quantity available (0 to " + MAX_QUANTITY + ") for " + name + ".");
            }
        }
    }

    private DailyMenuItem newItem(DailyMenuForm.Row row, MenuItem item) {
        DailyMenuItem d = new DailyMenuItem();
        d.setMenuItemId(item.getId());
        d.setName(item.getName());
        d.setDescription(item.getDescription());
        d.setCategory(item.getCategory());
        d.setSubcategory(item.getSubcategory());
        d.setImageUrl(item.getImageUrl());
        d.setPriceForThatDay(money(row.getPrice()));
        d.setAvailableQuantity(row.getQuantity());
        d.setAvailable(row.isAvailable());
        return d;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException("Choose a valid date.");
        }
    }

    // ---------- Stock ----------

    /**
     * Atomically takes 'quantity' portions of an item from the menu's stock.
     * The update only matches if the item is available AND has enough left, so two customers
     * can never both buy the last portion. Returns false if there was not enough stock.
     */
    public boolean reserveStock(String dailyMenuId, String menuItemId, int quantity) {
        Query query = new Query(Criteria.where("id").is(dailyMenuId)
                .and("items").elemMatch(Criteria.where("menuItemId").is(menuItemId)
                        .and("available").is(true)
                        .and("availableQuantity").gte(quantity)));
        return mongo.updateFirst(query, new Update().inc("items.$.availableQuantity", -quantity), DailyMenu.class)
                .getModifiedCount() == 1;
    }

    /** Puts portions back (used when an order is rejected or cancelled before preparation). */
    public void releaseStock(String dailyMenuId, String menuItemId, int quantity) {
        Query query = new Query(Criteria.where("id").is(dailyMenuId).and("items.menuItemId").is(menuItemId));
        mongo.updateFirst(query, new Update().inc("items.$.availableQuantity", quantity), DailyMenu.class);
    }
}

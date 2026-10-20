package com.campuscanteen.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.campuscanteen.model.DailyMenu;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Order;
import com.campuscanteen.repository.MenuItemRepository;
import com.campuscanteen.web.form.MenuItemForm;

/**
 * The permanent food catalogue. Items that have been used on a daily menu or in an order are
 * deactivated rather than deleted, because old orders and daily menus still refer to them.
 * Unused items (for example an unwanted draft) can be deleted.
 */
@Service
public class MenuItemService {

    /** Catalogue order: category as on the printed menu, then Chinese sub-section, then name. */
    public static final Comparator<MenuItem> CATALOGUE_ORDER = Comparator
            .comparingInt((MenuItem i) -> categoryPosition(i.getCategory()))
            .thenComparing(i -> i.getSubcategory() == null ? "" : i.getSubcategory())
            .thenComparing(MenuItem::getName, String.CASE_INSENSITIVE_ORDER);

    private final MenuItemRepository repository;
    private final MongoTemplate mongo;
    private final Clock clock;

    public MenuItemService(MenuItemRepository repository, MongoTemplate mongo, Clock clock) {
        this.repository = repository;
        this.mongo = mongo;
        this.clock = clock;
    }

    public static int categoryPosition(String category) {
        int index = MenuItem.CATEGORIES.indexOf(category);
        return index < 0 ? MenuItem.CATEGORIES.size() : index;
    }

    public List<MenuItem> listAll() {
        return repository.findAll().stream().sorted(CATALOGUE_ORDER).toList();
    }

    public List<MenuItem> listActive() {
        return listAll().stream().filter(MenuItem::isActive).toList();
    }

    public MenuItem get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Menu item not found"));
    }

    public MenuItem save(MenuItemForm form) {
        if (!MenuItem.CATEGORIES.contains(form.getCategory())) {
            throw new BusinessException("Choose a valid category.");
        }
        String subcategory = blankToNull(form.getSubcategory());
        if (MenuItem.CHINESE.equals(form.getCategory())) {
            if (subcategory != null && !MenuItem.CHINESE_SUBCATEGORIES.contains(subcategory)) {
                throw new BusinessException("Choose Rice, Noodles or Starters for a Chinese item.");
            }
        } else {
            subcategory = null;   // sub-sections are only used for Chinese food
        }
        boolean isNew = form.getId() == null || form.getId().isBlank();
        String name = form.getName().trim().replaceAll("\\s+", " ");
        findByName(name).filter(other -> isNew || !other.getId().equals(form.getId())).ifPresent(other -> {
            throw new BusinessException("There is already a food item called " + other.getName() + ".");
        });
        MenuItem item = isNew ? new MenuItem() : get(form.getId());
        Instant now = Instant.now(clock);
        if (isNew) {
            item.setCreatedAt(now);
        }
        item.setName(name);
        item.setDescription(blankToNull(form.getDescription()));
        item.setCategory(form.getCategory());
        item.setSubcategory(subcategory);
        item.setDefaultPrice(form.getDefaultPrice() == null ? null : form.getDefaultPrice().setScale(2, RoundingMode.HALF_UP));
        item.setImageUrl(blankToNull(form.getImageUrl()));
        item.setActive(form.isActive());
        item.setNameNeedsConfirmation(form.isNameNeedsConfirmation());
        item.setUpdatedAt(now);
        return repository.save(item);
    }

    /** Finds an item by name, ignoring upper/lower case and extra spaces. */
    public java.util.Optional<MenuItem> findByName(String name) {
        String wanted = name.trim().replaceAll("\\s+", " ");
        return repository.findAll().stream().filter(i -> i.getName().equalsIgnoreCase(wanted)).findFirst();
    }

    public void setActive(String id, boolean active) {
        MenuItem item = get(id);
        item.setActive(active);
        item.setUpdatedAt(Instant.now(clock));
        repository.save(item);
    }

    /** True if any daily menu or order refers to this item (then it must be kept and only deactivated). */
    public boolean isUsed(String id) {
        return mongo.exists(new Query(Criteria.where("items.menuItemId").is(id)), DailyMenu.class)
                || mongo.exists(new Query(Criteria.where("items.menuItemId").is(id)), Order.class);
    }

    /** IDs of all items that appear on any daily menu or order (two queries for the whole list page). */
    public java.util.Set<String> usedIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        ids.addAll(mongo.findDistinct(new Query(), "items.menuItemId", DailyMenu.class, String.class));
        ids.addAll(mongo.findDistinct(new Query(), "items.menuItemId", Order.class, String.class));
        return ids;
    }

    /** Deletes an item that was never used. Used items are refused, so history is never broken. */
    public MenuItem delete(String id) {
        MenuItem item = get(id);
        if (isUsed(id)) {
            throw new BusinessException(item.getName() + " has been on a daily menu or in an order, so it cannot be deleted. "
                    + "Deactivate it instead.");
        }
        repository.deleteById(id);
        return item;
    }

    public MenuItemForm toForm(MenuItem item) {
        MenuItemForm form = new MenuItemForm();
        form.setId(item.getId());
        form.setName(item.getName());
        form.setDescription(item.getDescription());
        form.setCategory(item.getCategory());
        form.setSubcategory(item.getSubcategory());
        form.setDefaultPrice(item.getDefaultPrice());
        form.setImageUrl(item.getImageUrl());
        form.setActive(item.isActive());
        form.setNameNeedsConfirmation(item.isNameNeedsConfirmation());
        return form;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

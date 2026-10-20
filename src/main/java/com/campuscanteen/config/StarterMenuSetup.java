package com.campuscanteen.config;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.campuscanteen.model.Canteen;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.repository.MenuItemRepository;
import com.campuscanteen.service.CanteenService;
import com.campuscanteen.service.MealSessionService;
import com.campuscanteen.service.MenuItemService;

/**
 * One-time starter data, run at startup by DatabaseInitializer:
 *
 * 1. The default meal sessions: Breakfast (opening time - 12:00), Lunch (12:00 - 15:00, with Chinese)
 *    and Snacks (15:00 - closing time, with Chinese).
 * 2. The food names that can be read clearly on the canteen's printed menu, as DRAFT items:
 *    no price (the poster has none), and unclear names are flagged "name needs confirmation".
 *    Drafts cannot be sold until an administrator enters a price on the daily menu.
 *
 * Each step runs only once per canteen. A flag on the canteen record is set with an atomic update
 * before the step runs, so restarting the application never adds anything again and never changes
 * prices, menus or edits made by an administrator (even an item the administrator deleted stays deleted).
 */
@Component
public class StarterMenuSetup {

    private static final Logger log = LoggerFactory.getLogger(StarterMenuSetup.class);

    /** Name, category, Chinese sub-section (or null), whether the name needs checking against the poster. */
    public record StarterItem(String name, String category, String subcategory, boolean nameNeedsConfirmation) {
    }

    public static final List<StarterItem> STARTER_ITEMS = List.of(
            // Breakfast / Tiffins
            item("Idly", MenuItem.BREAKFAST), item("Ragi Idly", MenuItem.BREAKFAST),
            item("Sambar Idly", MenuItem.BREAKFAST), item("Wada", MenuItem.BREAKFAST),
            item("Sambar Wada", MenuItem.BREAKFAST), item("Poori", MenuItem.BREAKFAST),
            unclear("Bonda", MenuItem.BREAKFAST),
            item("Plain Dosa", MenuItem.BREAKFAST), item("Masala Dosa", MenuItem.BREAKFAST),
            item("Onion Dosa", MenuItem.BREAKFAST), unclear("Karam Dosa", MenuItem.BREAKFAST),
            item("Paneer Dosa", MenuItem.BREAKFAST), item("Butter Dosa", MenuItem.BREAKFAST),
            item("Ghee Dosa", MenuItem.BREAKFAST), item("Upma Pesarattu", MenuItem.BREAKFAST),
            item("Onion Pesarattu", MenuItem.BREAKFAST), item("Upma Onion Pesarattu", MenuItem.BREAKFAST),
            item("Set Dosa", MenuItem.BREAKFAST),
            // Lunch
            item("Chapati", MenuItem.LUNCH), item("Meals", MenuItem.LUNCH),
            item("Veg Dum Biryani", MenuItem.LUNCH), item("Jeera Rice", MenuItem.LUNCH),
            item("Special Jeera Rice", MenuItem.LUNCH), item("Special Veg Pulao", MenuItem.LUNCH),
            item("Special Paneer Pulao", MenuItem.LUNCH), item("Special Sweet Corn Pulao", MenuItem.LUNCH),
            item("Special Mutter Pulao", MenuItem.LUNCH),
            // Snacks
            unclear("Punugulu", MenuItem.SNACKS), item("Mirchi Bajji", MenuItem.SNACKS),
            item("Onion Mirchi Bajji", MenuItem.SNACKS), item("Cut Mirchi", MenuItem.SNACKS),
            item("Veg Pakodi", MenuItem.SNACKS), item("Onion Pakodi", MenuItem.SNACKS),
            item("Palak Pakodi", MenuItem.SNACKS), item("Veg Cutlet", MenuItem.SNACKS),
            unclear("Ajwain Pakoda", MenuItem.SNACKS), item("Bread Bajji", MenuItem.SNACKS),
            item("Crispy Corn", MenuItem.SNACKS),
            // Chinese - Rice
            chinese("Veg Fried Rice", "Rice"), chinese("Schezwan Fried Rice", "Rice"),
            chinese("Paneer Fried Rice", "Rice"), chinese("Manchurian Fried Rice", "Rice"),
            chinese("Chilli Garlic Rice", "Rice"),
            // Chinese - Noodles
            chinese("Veg Noodles", "Noodles"), chinese("Manchurian Noodles", "Noodles"),
            chinese("Paneer Noodles", "Noodles"), chinese("Sweet Corn Noodles", "Noodles"),
            chinese("Schezwan Noodles", "Noodles"), chinese("Chilli Garlic Noodles", "Noodles"),
            chinese("Veg Hakka Noodles", "Noodles"),
            // Chinese - Starters
            chinese("Veg Manchurian", "Starters"), chinese("Veg 65", "Starters"),
            chinese("Gobi Manchurian", "Starters"), chinese("Gobi 65", "Starters"),
            chinese("Chilli Gobi", "Starters"), chinese("Baby Corn Manchurian", "Starters"),
            chinese("Chilli Baby Corn", "Starters"), chinese("Paneer 65", "Starters"),
            chinese("Chilli Paneer", "Starters"), chinese("Crispy Chilli Potato", "Starters"));

    private static StarterItem item(String name, String category) {
        return new StarterItem(name, category, null, false);
    }

    private static StarterItem unclear(String name, String category) {
        return new StarterItem(name, category, null, true);
    }

    private static StarterItem chinese(String name, String subcategory) {
        return new StarterItem(name, MenuItem.CHINESE, subcategory, false);
    }

    private final MongoTemplate mongo;
    private final CanteenService canteenService;
    private final MealSessionService mealSessionService;
    private final MenuItemService menuItemService;
    private final MenuItemRepository menuItemRepository;
    private final Clock clock;

    public StarterMenuSetup(MongoTemplate mongo, CanteenService canteenService, MealSessionService mealSessionService,
                            MenuItemService menuItemService, MenuItemRepository menuItemRepository, Clock clock) {
        this.mongo = mongo;
        this.canteenService = canteenService;
        this.mealSessionService = mealSessionService;
        this.menuItemService = menuItemService;
        this.menuItemRepository = menuItemRepository;
        this.clock = clock;
    }

    public void run() {
        Canteen canteen = canteenService.getDefaultCanteen();
        if (claimOnce(canteen, "defaultSessionsCreated")) {
            int created = mealSessionService.createDefaultSessions(canteen);
            log.info(created > 0
                    ? "Created the default meal sessions (Breakfast, Lunch, Snacks)."
                    : "Meal sessions already exist, so no default sessions were created.");
        }
        if (claimOnce(canteen, "starterMenuAdded")) {
            Instant now = Instant.now(clock);
            int added = 0;
            for (StarterItem starter : STARTER_ITEMS) {
                if (menuItemService.findByName(starter.name()).isPresent()) {
                    continue;   // keep the administrator's own item with this name
                }
                MenuItem item = new MenuItem();
                item.setName(starter.name());
                item.setCategory(starter.category());
                item.setSubcategory(starter.subcategory());
                item.setDefaultPrice(null);
                item.setNameNeedsConfirmation(starter.nameNeedsConfirmation());
                item.setActive(true);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                menuItemRepository.insert(item);
                added++;
            }
            log.info("Added {} draft food items from the printed menu (without prices).", added);
        }
    }

    /** Sets the flag if it is not set yet. Returns true only for the one caller that set it. */
    private boolean claimOnce(Canteen canteen, String flag) {
        return mongo.updateFirst(
                new Query(Criteria.where("id").is(canteen.getId()).and(flag).ne(true)),
                new Update().set(flag, true), Canteen.class).getModifiedCount() == 1;
    }
}

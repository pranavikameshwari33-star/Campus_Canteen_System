package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.campuscanteen.config.StarterMenuSetup;
import com.campuscanteen.service.FoodPhotos;

/** The bundled food photos: every file exists, and only known names get a photo of their own dish. */
class FoodPhotosTest {

    private final FoodPhotos photos = new FoodPhotos();

    @Test
    void everyBundledPhotoFileExists() {
        assertTrue(photos.all().size() >= 40);
        for (FoodPhotos.Photo photo : photos.all()) {
            assertNotNull(getClass().getResource("/static" + photo.url()), "missing file " + photo.url());
        }
        assertNotNull(getClass().getResource("/static/images/food/credits.html"));
    }

    @Test
    void suggestionsMatchTheDishName() {
        assertEquals("/images/food/idli.jpg", photos.suggestFor("Idly"));
        assertEquals("/images/food/idli.jpg", photos.suggestFor("  idli "));
        assertEquals("/images/food/masala-dosa.jpg", photos.suggestFor("Masala Dosa"));
        assertEquals("/images/food/veg-fried-rice.jpg", photos.suggestFor("Veg Fried Rice"));
        assertEquals("/images/food/meals.jpg", photos.suggestFor("Meals"));
        // No photo rather than a photo of a different dish.
        assertNull(photos.suggestFor("Veg 65"));
        assertNull(photos.suggestFor("Paneer Noodles"));
        assertNull(photos.suggestFor("Something New"));
        assertNull(photos.suggestFor(null));
    }

    @Test
    void chosenPhotoWinsOverTheSuggestion() {
        assertEquals("/files/menu-photo/x.jpg", photos.urlFor("/files/menu-photo/x.jpg", "Idly"));
        assertEquals("/images/food/idli.jpg", photos.urlFor(null, "Idly"));
        assertEquals("/images/food/idli.jpg", photos.urlFor(" ", "Idly"));
        assertTrue(photos.isCustom("https://example.org/dosa.jpg"));
        assertTrue(!photos.isCustom("/images/food/dosa.jpg") && !photos.isCustom(""));
    }

    @Test
    void mostStarterItemsHaveAPhotoOfTheirOwnDish() {
        List<String> without = new ArrayList<>();
        for (StarterMenuSetup.StarterItem item : StarterMenuSetup.STARTER_ITEMS) {
            if (photos.suggestFor(item.name()) == null) {
                without.add(item.name());
            }
        }
        // Deliberately left without a photo: no openly licensed photo of exactly that dish was found.
        assertEquals(List.of("Karam Dosa", "Special Sweet Corn Pulao", "Cut Mirchi", "Ajwain Pakoda",
                "Paneer Fried Rice", "Manchurian Fried Rice", "Chilli Garlic Rice", "Manchurian Noodles",
                "Paneer Noodles", "Sweet Corn Noodles", "Chilli Garlic Noodles", "Veg 65", "Chilli Gobi"), without);
    }
}

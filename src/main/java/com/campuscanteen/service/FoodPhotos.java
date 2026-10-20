package com.campuscanteen.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The food photos that come with the application (src/main/resources/static/images/food).
 * They are real photos of each dish from Wikimedia Commons; authors and licences are listed
 * on /images/food/credits.html.
 *
 * A food item shows, in this order:
 * 1. the photo the administrator chose or uploaded (stored in the item's imageUrl), or
 * 2. the bundled photo suggested for its name (only exact, known names, so an item never shows
 *    a photo of a different dish), or
 * 3. a plain "No photo" placeholder.
 * The suggestion is worked out when the page is shown, so no database record has to change.
 */
@Component("photos")
public class FoodPhotos {

    public static final String BASE = "/images/food/";

    public record Photo(String file, String label) {
        public String url() {
            return BASE + file;
        }
    }

    /** Every bundled photo, in menu order (shown in the administrator's photo list). */
    private static final List<Photo> ALL = List.of(
            new Photo("idli.jpg", "Idli"), new Photo("sambar-idli.jpg", "Sambar idli"),
            new Photo("ragi-idli.jpg", "Ragi idli"), new Photo("vada.jpg", "Vada (medu vada)"),
            new Photo("sambar-vada.jpg", "Sambar vada"), new Photo("poori.jpg", "Poori"),
            new Photo("bonda.jpg", "Bonda"), new Photo("dosa.jpg", "Plain dosa"),
            new Photo("masala-dosa.jpg", "Masala dosa"), new Photo("onion-dosa.jpg", "Onion dosa"),
            new Photo("paneer-dosa.jpg", "Paneer dosa"), new Photo("butter-dosa.jpg", "Butter dosa"),
            new Photo("ghee-dosa.jpg", "Ghee roast dosa"), new Photo("set-dosa.jpg", "Set dosa"),
            new Photo("pesarattu.jpg", "Pesarattu"), new Photo("upma-pesarattu.jpg", "Upma pesarattu"),
            new Photo("chapati.jpg", "Chapati"), new Photo("meals.jpg", "Meals (thali)"),
            new Photo("veg-biryani.jpg", "Veg biryani"), new Photo("jeera-rice.jpg", "Jeera rice"),
            new Photo("veg-pulao.jpg", "Veg pulao"), new Photo("paneer-pulao.jpg", "Paneer pulao"),
            new Photo("peas-pulao.jpg", "Mutter (peas) pulao"), new Photo("punugulu.jpg", "Punugulu"),
            new Photo("mirchi-bajji.jpg", "Mirchi bajji"), new Photo("onion-pakoda.jpg", "Onion pakodi"),
            new Photo("pakoda.jpg", "Pakodi"), new Photo("palak-pakoda.jpg", "Palak pakodi"),
            new Photo("veg-cutlet.jpg", "Veg cutlet"), new Photo("bread-bajji.jpg", "Bread bajji"),
            new Photo("crispy-corn.jpg", "Crispy corn"), new Photo("chilli-baby-corn.jpg", "Chilli baby corn"),
            new Photo("baby-corn-manchurian.jpg", "Baby corn manchurian"),
            new Photo("veg-fried-rice.jpg", "Veg fried rice"), new Photo("schezwan-fried-rice.jpg", "Schezwan fried rice"),
            new Photo("veg-noodles.jpg", "Veg noodles"), new Photo("hakka-noodles.jpg", "Hakka noodles"),
            new Photo("schezwan-noodles.jpg", "Schezwan noodles"), new Photo("veg-manchurian.jpg", "Veg manchurian"),
            new Photo("gobi-manchurian.jpg", "Gobi manchurian"), new Photo("gobi-65.jpg", "Gobi 65"),
            new Photo("paneer-65.jpg", "Paneer 65"), new Photo("chilli-paneer.jpg", "Chilli paneer"),
            new Photo("chilli-potato.jpg", "Crispy chilli potato"));

    /**
     * Food names (lower case) and the photo suggested for them. Variants of a dish share its photo
     * (e.g. Special Jeera Rice). Names without a fitting photo (e.g. Veg 65, Cut Mirchi) are left out on purpose.
     */
    private static final Map<String, String> BY_NAME = new LinkedHashMap<>();

    static {
        name("idly", "idli.jpg"); name("idli", "idli.jpg");
        name("sambar idly", "sambar-idli.jpg"); name("sambar idli", "sambar-idli.jpg");
        name("ragi idly", "ragi-idli.jpg"); name("ragi idli", "ragi-idli.jpg");
        name("wada", "vada.jpg"); name("vada", "vada.jpg"); name("medu vada", "vada.jpg");
        name("sambar wada", "sambar-vada.jpg"); name("sambar vada", "sambar-vada.jpg");
        name("poori", "poori.jpg"); name("puri", "poori.jpg");
        name("bonda", "bonda.jpg");
        name("plain dosa", "dosa.jpg"); name("dosa", "dosa.jpg");
        name("masala dosa", "masala-dosa.jpg");
        name("onion dosa", "onion-dosa.jpg");
        name("paneer dosa", "paneer-dosa.jpg");
        name("butter dosa", "butter-dosa.jpg");
        name("ghee dosa", "ghee-dosa.jpg"); name("ghee roast dosa", "ghee-dosa.jpg");
        name("set dosa", "set-dosa.jpg");
        name("pesarattu", "pesarattu.jpg"); name("onion pesarattu", "pesarattu.jpg");
        name("upma pesarattu", "upma-pesarattu.jpg"); name("upma onion pesarattu", "upma-pesarattu.jpg");
        name("chapati", "chapati.jpg"); name("chapathi", "chapati.jpg");
        name("meals", "meals.jpg");
        name("veg dum biryani", "veg-biryani.jpg"); name("veg biryani", "veg-biryani.jpg");
        name("jeera rice", "jeera-rice.jpg"); name("special jeera rice", "jeera-rice.jpg");
        name("veg pulao", "veg-pulao.jpg"); name("special veg pulao", "veg-pulao.jpg");
        name("paneer pulao", "paneer-pulao.jpg"); name("special paneer pulao", "paneer-pulao.jpg");
        name("mutter pulao", "peas-pulao.jpg"); name("special mutter pulao", "peas-pulao.jpg");
        name("punugulu", "punugulu.jpg");
        name("mirchi bajji", "mirchi-bajji.jpg"); name("onion mirchi bajji", "mirchi-bajji.jpg");
        name("onion pakodi", "onion-pakoda.jpg"); name("onion pakoda", "onion-pakoda.jpg");
        name("veg pakodi", "pakoda.jpg"); name("veg pakoda", "pakoda.jpg");
        name("palak pakodi", "palak-pakoda.jpg"); name("palak pakoda", "palak-pakoda.jpg");
        name("veg cutlet", "veg-cutlet.jpg");
        name("bread bajji", "bread-bajji.jpg");
        name("crispy corn", "crispy-corn.jpg");
        name("chilli baby corn", "chilli-baby-corn.jpg");
        name("baby corn manchurian", "baby-corn-manchurian.jpg");
        name("veg fried rice", "veg-fried-rice.jpg");
        name("schezwan fried rice", "schezwan-fried-rice.jpg");
        name("veg noodles", "veg-noodles.jpg");
        name("veg hakka noodles", "hakka-noodles.jpg"); name("hakka noodles", "hakka-noodles.jpg");
        name("schezwan noodles", "schezwan-noodles.jpg");
        name("veg manchurian", "veg-manchurian.jpg");
        name("gobi manchurian", "gobi-manchurian.jpg");
        name("gobi 65", "gobi-65.jpg");
        name("paneer 65", "paneer-65.jpg");
        name("chilli paneer", "chilli-paneer.jpg");
        name("crispy chilli potato", "chilli-potato.jpg"); name("chilli potato", "chilli-potato.jpg");
    }

    private static void name(String foodName, String file) {
        BY_NAME.put(foodName, file);
    }

    public List<Photo> all() {
        return ALL;
    }

    /** The bundled photo for this food name, or null if there is none. */
    public String suggestFor(String foodName) {
        if (foodName == null) {
            return null;
        }
        String file = BY_NAME.get(foodName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
        return file == null ? null : BASE + file;
    }

    /** The photo to show: the one chosen for the item, otherwise the suggestion for its name (may be null). */
    public String urlFor(String imageUrl, String foodName) {
        return imageUrl != null && !imageUrl.isBlank() ? imageUrl : suggestFor(foodName);
    }

    /** True if the address is one of the bundled photos. */
    public static boolean isBundled(String url) {
        return url != null && ALL.stream().anyMatch(p -> p.url().equals(url));
    }

    /** True for a photo the administrator uploaded or linked (not a bundled one). Used by the item form. */
    public boolean isCustom(String url) {
        return url != null && !url.isBlank() && !isBundled(url);
    }
}

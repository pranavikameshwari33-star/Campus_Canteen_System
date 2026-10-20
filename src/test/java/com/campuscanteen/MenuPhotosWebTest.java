package com.campuscanteen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.campuscanteen.model.MealSession;
import com.campuscanteen.model.MenuItem;
import com.campuscanteen.model.Role;
import com.campuscanteen.security.CanteenUserDetails;

/** Food photos: choosing, uploading and keeping a photo, and showing it on the customer menu. */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class MenuPhotosWebTest extends IntegrationTestBase {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired MockMvc mvc;

    @Test
    void customerMenuShowsTheMatchingPhotoOrAPlaceholder() throws Exception {
        MenuItem dosa = menuItem("Masala Dosa", "45");
        MenuItem other = menuItem("Lemon Rice Special", "40");
        MealSession lunch = lunch();
        publishMenu(lunch, Map.of(dosa, 5, other, 5));
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS501"));

        String page = mvc.perform(get("/menu").with(user(student)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("src=\"/images/food/masala-dosa.jpg\""), "Masala Dosa shows its own photo");
        assertTrue(page.contains("No photo"), "an item without a matching photo shows the placeholder");
        assertTrue(page.contains("food-card"));

        // The bundled photos and the credits page can be loaded.
        mvc.perform(get("/images/food/masala-dosa.jpg")).andExpect(status().isOk());
        mvc.perform(get("/images/food/credits.html")).andExpect(status().isOk());
    }

    @Test
    void administratorChoosesUploadsAndKeepsPhotos() throws Exception {
        CanteenUserDetails admin = new CanteenUserDetails(admin("ADMIN7"));

        // A bundled photo chosen from the list is stored as a reference in MongoDB.
        mvc.perform(multipart("/admin/menu-items").with(user(admin)).with(csrf())
                        .param("name", "Plain Dosa").param("category", "Breakfast").param("active", "true")
                        .param("imageUrl", "/images/food/dosa.jpg"))
                .andExpect(status().is3xxRedirection());
        MenuItem dosa = menuItemService.findByName("Plain Dosa").orElseThrow();
        assertEquals("/images/food/dosa.jpg", dosa.getImageUrl());

        // Editing other fields keeps the photo (the form sends the current choice back).
        mvc.perform(multipart("/admin/menu-items").with(user(admin)).with(csrf())
                        .param("id", dosa.getId()).param("name", "Plain Dosa").param("category", "Breakfast")
                        .param("defaultPrice", "35").param("active", "true").param("imageUrl", dosa.getImageUrl()))
                .andExpect(status().is3xxRedirection());
        assertEquals("/images/food/dosa.jpg", menuItemService.get(dosa.getId()).getImageUrl());

        // The edit page shows the chosen photo.
        mvc.perform(get("/admin/menu-items/" + dosa.getId() + "/edit").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("src=\"/images/food/dosa.jpg\"")));

        // An uploaded photo is saved on disk; MongoDB keeps only its address.
        MockMultipartFile photo = new MockMultipartFile("photo", "my-dosa.jpg", "image/jpeg", JPEG_BYTES);
        mvc.perform(multipart("/admin/menu-items").file(photo).with(user(admin)).with(csrf())
                        .param("id", dosa.getId()).param("name", "Plain Dosa").param("category", "Breakfast")
                        .param("active", "true").param("imageUrl", "/images/food/dosa.jpg"))
                .andExpect(status().is3xxRedirection());
        String uploaded = menuItemService.get(dosa.getId()).getImageUrl();
        assertTrue(uploaded.matches("/files/menu-photo/[a-f0-9-]{36}\\.jpg"), uploaded);

        // Customers can see the uploaded photo.
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS502"));
        mvc.perform(get(uploaded).with(user(student)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void unsafePhotosAreRejected() throws Exception {
        CanteenUserDetails admin = new CanteenUserDetails(admin("ADMIN8"));

        // A file that is not a real image is refused and nothing is saved.
        MockMultipartFile fake = new MockMultipartFile("photo", "idli.jpg", "image/jpeg", "not an image".getBytes());
        mvc.perform(multipart("/admin/menu-items").file(fake).with(user(admin)).with(csrf())
                        .param("name", "Idly").param("category", "Breakfast").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Only JPEG or PNG images are accepted.")));
        assertTrue(menuItemService.findByName("Idly").isEmpty());

        // Addresses other than a bundled photo, an uploaded photo or a web link are refused.
        for (String bad : new String[] {"javascript:alert(1)", "/images/food/../../application.properties", "/files/menu-photo/../x.jpg"}) {
            mvc.perform(post("/admin/menu-items").with(user(admin)).with(csrf())
                            .param("name", "Idly").param("category", "Breakfast").param("active", "true").param("imageUrl", bad))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("Choose a photo from the list")));
        }
        assertTrue(menuItemService.findByName("Idly").isEmpty());

        // Students cannot change food items.
        CanteenUserDetails student = new CanteenUserDetails(customer(Role.STUDENT, "21CS503"));
        mvc.perform(post("/admin/menu-items").with(user(student)).with(csrf())
                        .param("name", "Idly").param("category", "Breakfast").param("imageUrl", "/images/food/idli.jpg"))
                .andExpect(status().isForbidden());

        // Requests for files outside the photo folder are not served.
        mvc.perform(get("/files/menu-photo/..%2F..%2Fapplication.properties").with(user(student)))
                .andExpect(status().is4xxClientError());

        // A typed web link is still accepted.
        mvc.perform(post("/admin/menu-items").with(user(admin)).with(csrf())
                        .param("name", "Idly").param("category", "Breakfast").param("active", "true")
                        .param("photoLink", "https://example.org/idli.jpg"))
                .andExpect(status().is3xxRedirection());
        assertEquals("https://example.org/idli.jpg", menuItemService.findByName("Idly").orElseThrow().getImageUrl());

        // Without any choice, nothing is stored: the photo for the name is shown automatically.
        mvc.perform(post("/admin/menu-items").with(user(admin)).with(csrf())
                        .param("name", "Poori").param("category", "Breakfast").param("active", "true"))
                .andExpect(status().is3xxRedirection());
        assertNull(menuItemService.findByName("Poori").orElseThrow().getImageUrl());
    }
}

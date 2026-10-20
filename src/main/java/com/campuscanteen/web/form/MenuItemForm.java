package com.campuscanteen.web.form;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MenuItemForm {

    private String id;

    @NotBlank(message = "Enter the item name.")
    @Size(max = 60, message = "Name is too long.")
    private String name;

    @Size(max = 200, message = "Description is too long (200 characters maximum).")
    private String description;

    @NotBlank(message = "Choose a category.")
    private String category;

    private String subcategory;

    /** Optional: leave empty for a draft item whose price is not known yet. */
    @DecimalMin(value = "0.01", message = "Price must be more than zero.")
    @DecimalMax(value = "10000.00", message = "Price is too high.")
    @Digits(integer = 5, fraction = 2, message = "Price can have at most 2 decimal places.")
    private BigDecimal defaultPrice;

    /**
     * The photo: empty (automatic: the bundled photo for the name, if any), a bundled photo
     * (/images/food/...), an uploaded photo (/files/menu-photo/...) or a web link.
     */
    @Size(max = 500, message = "Image link is too long.")
    @Pattern(regexp = "^$|^https?://.+|^/images/food/[a-z0-9-]+\\.jpg$|^/files/menu-photo/[a-f0-9-]{36}\\.(jpg|png)$",
            message = "Choose a photo from the list, upload one, or enter a link starting with http:// or https://")
    private String imageUrl;

    /** Optional web link typed by the administrator; replaces the photo chosen in the list. */
    @Size(max = 500, message = "Image link is too long.")
    @Pattern(regexp = "^$|^https?://.+", message = "Image link must start with http:// or https://")
    private String photoLink;

    private boolean active = true;

    private boolean nameNeedsConfirmation;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public BigDecimal getDefaultPrice() { return defaultPrice; }
    public void setDefaultPrice(BigDecimal defaultPrice) { this.defaultPrice = defaultPrice; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getPhotoLink() { return photoLink; }
    public void setPhotoLink(String photoLink) { this.photoLink = photoLink == null ? null : photoLink.trim(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isNameNeedsConfirmation() { return nameNeedsConfirmation; }
    public void setNameNeedsConfirmation(boolean nameNeedsConfirmation) { this.nameNeedsConfirmation = nameNeedsConfirmation; }
}

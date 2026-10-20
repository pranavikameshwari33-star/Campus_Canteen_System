package com.campuscanteen.web.form;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.campuscanteen.model.MealType;

public class MealSessionForm {

    private String id;

    @NotBlank(message = "Enter a name, e.g. Lunch.")
    @Size(max = 40, message = "Name is too long.")
    private String name;

    private MealType type = MealType.OTHER;

    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "Start time must look like 08:30.")
    private String startTime;

    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "End time must look like 10:30.")
    private String endTime;

    /** Food categories sold in this session (ticked on the form). */
    private List<String> allowedCategories = new ArrayList<>();

    private boolean active = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public MealType getType() { return type; }
    public void setType(MealType type) { this.type = type; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public List<String> getAllowedCategories() { return allowedCategories; }
    public void setAllowedCategories(List<String> allowedCategories) { this.allowedCategories = allowedCategories; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

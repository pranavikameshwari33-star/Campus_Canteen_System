package com.campuscanteen.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Data typed into the administrator registration form (/admin/register). */
public class AdminRegistrationForm {

    @NotBlank(message = "Enter an administrator ID.")
    @Pattern(regexp = "^[A-Za-z0-9/-]{3,30}$",
            message = "Administrator ID must be 3-30 characters: letters, digits, '-' or '/'.")
    private String collegeId;

    @NotBlank(message = "Enter your name.")
    @Size(max = 80, message = "Name is too long.")
    private String name;

    @NotBlank(message = "Enter a password.")
    @Size(min = 8, max = 64, message = "Password must be 8-64 characters.")
    private String password;

    @NotBlank(message = "Confirm your password.")
    private String confirmPassword;

    /** Invitation code (or, for the very first administrator, the optional setup code). */
    @Size(max = 40, message = "The code is too long.")
    private String code;

    public String getCollegeId() { return collegeId; }
    public void setCollegeId(String collegeId) { this.collegeId = collegeId == null ? null : collegeId.trim(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}

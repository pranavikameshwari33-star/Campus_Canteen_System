package com.campuscanteen.web.form;

import com.campuscanteen.model.Role;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Data typed into the registration form. Validated on the server before anything is saved. */
public class RegistrationForm {

    @NotNull(message = "Choose Student or Faculty.")
    private Role role;

    @NotBlank(message = "Enter your roll number or faculty ID.")
    @Pattern(regexp = "^[A-Za-z0-9/-]{3,30}$",
            message = "College ID must be 3-30 characters: letters, digits, '-' or '/'.")
    private String collegeId;

    @NotBlank(message = "Enter your name.")
    @Size(max = 80, message = "Name is too long.")
    private String name;

    @NotBlank(message = "Enter your department.")
    @Size(max = 80, message = "Department is too long.")
    private String department;

    @Min(value = 1, message = "Year must be between 1 and 6.")
    @Max(value = 6, message = "Year must be between 1 and 6.")
    private Integer year;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Phone number must be 10 digits.")
    private String phone;

    @NotBlank(message = "Enter a password.")
    @Size(min = 8, max = 64, message = "Password must be 8-64 characters.")
    private String password;

    @NotBlank(message = "Confirm your password.")
    private String confirmPassword;

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getCollegeId() { return collegeId; }
    /** Spaces around the ID (easy to paste by accident) are removed before validation. */
    public void setCollegeId(String collegeId) { this.collegeId = collegeId == null ? null : collegeId.trim(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}

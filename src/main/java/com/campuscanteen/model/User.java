package com.campuscanteen.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One account in the "users" collection: a student, a faculty member or a canteen administrator.
 * collegeId is stored in upper case and is unique across all users (unique index).
 */
@Document("users")
public class User {

    @Id
    private String id;
    private String name;
    private String collegeId;
    private Role role;
    private String department;
    /** Year of study. Only used for students. */
    private Integer year;
    private String phone;
    /** BCrypt hash. The plain password is never stored. */
    private String passwordHash;
    @Field("isActive")
    private boolean active = true;
    /** For canteen administrators: the canteen they manage. */
    private String canteenId;
    private Instant createdAt;
    /** When the password was last reset with tools/AdminPasswordReset (null if never). */
    private Instant passwordChangedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCollegeId() { return collegeId; }
    public void setCollegeId(String collegeId) { this.collegeId = collegeId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCanteenId() { return canteenId; }
    public void setCanteenId(String canteenId) { this.canteenId = canteenId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(Instant passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }
}

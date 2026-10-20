package com.campuscanteen.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;

/**
 * The logged-in user, as Spring Security sees it. Holds only what pages need
 * (id, college ID, name, role); the password hash is used during login only.
 */
public class CanteenUserDetails implements UserDetails {

    private final String id;
    private final String collegeId;
    private final String name;
    private final Role role;
    private final String passwordHash;
    private final boolean active;

    public CanteenUserDetails(User user) {
        this.id = user.getId();
        this.collegeId = user.getCollegeId();
        this.name = user.getName();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
    }

    public String getId() { return id; }
    public String getCollegeId() { return collegeId; }
    public String getName() { return name; }
    public Role getRole() { return role; }

    public boolean isAdmin() {
        return role == Role.CANTEEN_ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return collegeId;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}

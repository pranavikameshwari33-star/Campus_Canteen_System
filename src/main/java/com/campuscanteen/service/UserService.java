package com.campuscanteen.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;
import com.campuscanteen.repository.UserRepository;
import com.campuscanteen.web.form.RegistrationForm;

/**
 * Creates user accounts. Passwords are hashed with BCrypt before saving.
 *
 * Note: the college ID is checked for format and uniqueness only. It is NOT verified
 * against an official college roster, because no roster is connected to this system.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongo;
    private final Clock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, MongoTemplate mongo, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongo = mongo;
        this.clock = clock;
    }

    /** College IDs are compared in upper case without surrounding spaces. */
    public static String normalizeCollegeId(String collegeId) {
        return collegeId == null ? "" : collegeId.trim().toUpperCase(Locale.ROOT);
    }

    /** Registers a student or faculty member. Administrator accounts cannot be created here. */
    public User registerCustomer(RegistrationForm form) {
        if (form.getRole() == null || !form.getRole().isCustomer()) {
            throw new BusinessException("Choose Student or Faculty.");
        }
        if (form.getRole() == Role.STUDENT && form.getYear() == null) {
            throw new BusinessException("Students must select their year of study.");
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("The two passwords do not match.");
        }
        User user = new User();
        user.setRole(form.getRole());
        user.setCollegeId(normalizeCollegeId(form.getCollegeId()));
        user.setName(form.getName().trim());
        user.setDepartment(form.getDepartment().trim());
        user.setYear(form.getRole() == Role.STUDENT ? form.getYear() : null);
        user.setPhone(form.getPhone() == null || form.getPhone().isBlank() ? null : form.getPhone().trim());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        return saveNewUser(user);
    }

    /** Creates a canteen administrator. Only called by the first-administrator setup. */
    public User createAdmin(String collegeId, String name, String rawPassword, String canteenId) {
        User user = new User();
        user.setRole(Role.CANTEEN_ADMIN);
        user.setCollegeId(normalizeCollegeId(collegeId));
        user.setName(name.trim());
        user.setDepartment("Canteen");
        user.setCanteenId(canteenId);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return saveNewUser(user);
    }

    private User saveNewUser(User user) {
        if (userRepository.existsByCollegeId(user.getCollegeId())) {
            throw new BusinessException("An account with this college ID already exists.");
        }
        user.setActive(true);
        user.setCreatedAt(Instant.now(clock));
        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException e) {
            // Two registrations with the same ID at the same moment: the unique index stops the second one.
            throw new BusinessException("An account with this college ID already exists.");
        }
    }

    public Optional<User> findByCollegeId(String collegeId) {
        return userRepository.findByCollegeId(normalizeCollegeId(collegeId));
    }

    /**
     * Student and faculty accounts, newest first (at most 200). If a search text is given, only
     * accounts whose college ID starts with it are returned.
     */
    public List<User> listCustomers(String search) {
        Criteria criteria = Criteria.where("role").in(Role.STUDENT, Role.FACULTY);
        String prefix = normalizeCollegeId(search);
        if (!prefix.isEmpty()) {
            criteria = criteria.and("collegeId").regex("^" + Pattern.quote(prefix));
        }
        return mongo.find(new Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(200), User.class);
    }

    /**
     * Disables or re-enables a student or faculty account. A disabled account cannot log in or place
     * orders; its past orders are kept. Administrator accounts cannot be changed here.
     */
    public User setCustomerActive(String userId, boolean active) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getRole().isCustomer())
                .orElseThrow(() -> new NotFoundException("Account not found"));
        user.setActive(active);
        return userRepository.save(user);
    }
}

package com.campuscanteen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.campuscanteen.security.CanteenUserDetails;

/**
 * Who may open which page. Every rule is checked on the server, so hiding a link is never
 * the only protection.
 *
 * - Public: welcome page, login, registration (customer and administrator), CSS/JS and the bundled food photos.
 *   /admin/register itself only creates an administrator for the very first setup or with a
 *   valid invitation code (see AdminRegistrationService).
 * - /admin/**: canteen administrators only.
 * - Ordering pages: students and faculty only (same rules for both).
 * CSRF protection is on (Thymeleaf adds the hidden token to every form automatically).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/admin/login", "/admin/register", "/register", "/css/**", "/js/**", "/images/**", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("CANTEEN_ADMIN")
                .requestMatchers("/home", "/menu/**", "/cart/**", "/checkout/**", "/orders/**")
                    .hasAnyRole("STUDENT", "FACULTY")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("collegeId")
                .passwordParameter("password")
                .successHandler(roleBasedSuccessHandler())
                .failureHandler(loginFailureHandler())
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }

    /** After login, administrators go to the admin dashboard and customers to their home page. */
    private AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (request, response, authentication) -> {
            CanteenUserDetails user = (CanteenUserDetails) authentication.getPrincipal();
            response.sendRedirect(request.getContextPath() + (user.isAdmin() ? "/admin" : "/home"));
        };
    }

    /**
     * A failed login returns to the login page it came from (customer or administrator).
     * Wrong ID, wrong password and disabled account all show the same message, so nobody can find out
     * which IDs exist. If the database could not be reached, a different message says so honestly.
     */
    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            boolean fromAdminPage = "admin".equals(request.getParameter("source"));
            String reason = exception instanceof InternalAuthenticationServiceException ? "unavailable" : "error";
            response.sendRedirect(request.getContextPath() + (fromAdminPage ? "/admin/login?" : "/login?") + reason);
        };
    }
}

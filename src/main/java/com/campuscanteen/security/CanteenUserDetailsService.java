package com.campuscanteen.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.campuscanteen.repository.UserRepository;
import com.campuscanteen.service.UserService;

/** Tells Spring Security how to find a user by college ID during login. */
@Service
public class CanteenUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CanteenUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String collegeId) {
        return userRepository.findByCollegeId(UserService.normalizeCollegeId(collegeId))
                .map(CanteenUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown college ID"));
    }
}

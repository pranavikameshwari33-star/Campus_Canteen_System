package com.campuscanteen.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.campuscanteen.model.Role;
import com.campuscanteen.model.User;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByCollegeId(String collegeId);

    boolean existsByCollegeId(String collegeId);

    boolean existsByRole(Role role);
}

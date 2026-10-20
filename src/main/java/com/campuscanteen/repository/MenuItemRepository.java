package com.campuscanteen.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.campuscanteen.model.MenuItem;

public interface MenuItemRepository extends MongoRepository<MenuItem, String> {
}

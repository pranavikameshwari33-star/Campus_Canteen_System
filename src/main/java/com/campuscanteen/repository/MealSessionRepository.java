package com.campuscanteen.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.campuscanteen.model.MealSession;

public interface MealSessionRepository extends MongoRepository<MealSession, String> {

    List<MealSession> findByCanteenIdOrderByStartTimeAsc(String canteenId);
}

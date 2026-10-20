package com.campuscanteen.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.campuscanteen.model.DailyMenu;

public interface DailyMenuRepository extends MongoRepository<DailyMenu, String> {

    Optional<DailyMenu> findByCanteenIdAndDateAndMealSessionId(String canteenId, String date, String mealSessionId);

    List<DailyMenu> findByCanteenIdAndDate(String canteenId, String date);
}

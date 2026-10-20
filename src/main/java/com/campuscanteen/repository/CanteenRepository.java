package com.campuscanteen.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.campuscanteen.model.Canteen;

public interface CanteenRepository extends MongoRepository<Canteen, String> {

    Optional<Canteen> findFirstByActiveTrueOrderByIdAsc();
}

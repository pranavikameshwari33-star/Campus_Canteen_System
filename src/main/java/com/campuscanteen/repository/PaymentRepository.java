package com.campuscanteen.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.campuscanteen.model.Payment;
import com.campuscanteen.model.PaymentStatus;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByStatusOrderByCreatedAtAsc(PaymentStatus status);
}

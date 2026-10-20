package com.campuscanteen.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.campuscanteen.model.Order;
import com.campuscanteen.model.OrderStatus;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Order> findByIdAndUserId(String id, String userId);

    Optional<Order> findByUserIdAndCheckoutKey(String userId, String checkoutKey);

    long countByUserIdAndOrderStatus(String userId, OrderStatus status);

    List<Order> findByCanteenIdAndOrderDateOrderByCreatedAtAsc(String canteenId, String orderDate);

    List<Order> findByCanteenIdAndOrderDateAndOrderStatusInOrderByUpdatedAtDesc(
            String canteenId, String orderDate, Collection<OrderStatus> statuses);

    long countByCanteenIdAndOrderStatus(String canteenId, OrderStatus status);
}

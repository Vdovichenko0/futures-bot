package io.cryptobot.binance.order.dao;

import io.cryptobot.binance.order.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order,Long> {
//    Order findByOrderId(Long id);
}

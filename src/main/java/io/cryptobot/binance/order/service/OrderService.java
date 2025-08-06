package io.cryptobot.binance.order.service;

import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.model.Order;

public interface OrderService {
    void updateOrder(Order updatedOrder);

    Order createOrder(String symbol, Double amount, OrderSide side, Boolean hedgeMode);

    Order closeOrder(Order order);

    Order getOrder(Long idOrder);

    Order getOrderFromBinance(Long idOrder, String symbol);
}

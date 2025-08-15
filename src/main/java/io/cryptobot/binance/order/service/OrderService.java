package io.cryptobot.binance.order.service;

import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;

import java.math.BigDecimal;

public interface OrderService {
    void updateOrder(Order updatedOrder);

    Order createOrder(String symbol, Double amount, OrderSide side, Boolean hedgeMode);

    Order createLimitOrElseMarket(String symbol, Double amount, OrderSide side, SizeModel sizes);

    Order closeOrder(Order order);

    Order closeOrder(BigDecimal count, OrderSide closingSide, String symbol, TradingDirection direction);

    Order closeOrder(TradeOrder order);

    Order getOrder(Long idOrder);

    Order getOrderFromBinance(Long idOrder, String symbol);
}

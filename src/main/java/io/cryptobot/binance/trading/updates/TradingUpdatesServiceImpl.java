package io.cryptobot.binance.trading.updates;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingUpdatesServiceImpl implements TradingUpdatesService{
    private final TradePlanGetService tradePlanGetService;
    private final TradeSessionService sessionService;
    private final OrderService orderService;

    @Override
    public TradeSession closePosition(TradeSession session,SessionMode sessionMode, Long idOrder, Long relatedHedgeId, TradingDirection direction, OrderPurpose purpose, BigDecimal currentPrice, String context){
        TradePlan tradePlan = tradePlanGetService.getPlan(session.getTradePlan());
        String coin = session.getTradePlan();
        TradeOrder entryOrder = session.getOrders().stream()
                .filter(o -> o.getOrderId().equals(idOrder))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Order not found: " + idOrder));

        BigDecimal entryPrice = entryOrder.getPrice();
        BigDecimal count      = entryOrder.getCount();

        OrderSide closeSide = entryOrder.getSide() == OrderSide.BUY
                ? OrderSide.SELL
                : OrderSide.BUY;

        //create order
        Order orderOpen = orderService.createOrder(coin, count.doubleValue(), closeSide, true);

        //check filled
        boolean filled = waitForFilledOrder(orderOpen, 15000, 2000);
        if (!filled) {
            log.warn("Order {} was not filled in time", orderOpen.getOrderId());
            return session;
        }

        Order filledOrder = orderService.getOrder(orderOpen.getOrderId());

        //calc pnl
        BigDecimal pnlFraction;
        if (direction == TradingDirection.LONG) {
            pnlFraction = filledOrder.getAveragePrice()
                    .subtract(entryPrice)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP);
        } else {
            pnlFraction = entryPrice
                    .subtract(filledOrder.getAveragePrice())
                    .divide(entryPrice, 8, RoundingMode.HALF_UP);
        }
        // pnlUSDT
        BigDecimal pnlAbsolute = pnlFraction
                .multiply(count)
                .multiply(entryPrice)
                .setScale(8, RoundingMode.HALF_UP);

        //build trade order
        TradeOrder newOrder = new TradeOrder();
        newOrder.onCreate(filledOrder, pnlAbsolute, sessionMode, context, tradePlan, direction, purpose, idOrder, relatedHedgeId);
        TradeSession updatesSession = sessionService.addOrder(session.getId(), newOrder);
        log.error("session status {}", session.getStatus());
        return updatesSession;
    }

    @Override
    public TradeSession openPosition(TradeSession session,SessionMode sessionMode, TradingDirection direction, OrderPurpose purpose, BigDecimal currentPrice, String context, Long parentOrderId, Long relatedHedgeId){
        TradePlan plan = tradePlanGetService.getPlan(session.getTradePlan());

        String coin = plan.getSymbol();
        BigDecimal amount = plan.getAmountPerTrade();
        BigDecimal lotSize = plan.getSizes().getLotSize();
        BigDecimal count = amount.divide(currentPrice, 8, RoundingMode.DOWN);

        if (count.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid quantity for " + coin + count);
        }
        count = count.divide(lotSize, 0, RoundingMode.DOWN).multiply(lotSize);
        OrderSide side = direction.equals(TradingDirection.SHORT) ? OrderSide.SELL : OrderSide.BUY;
        Order orderOpen = orderService.createOrder(coin, count.doubleValue(), side, true);
        boolean filled = waitForFilledOrder(orderOpen, 5000, 500);
        if (!filled) {
            log.warn("Order {} was not filled in time", orderOpen.getOrderId());
            return session;
        }
        Order filledOrder = orderService.getOrder(orderOpen.getOrderId());
        TradeOrder newOrder = new TradeOrder();
        newOrder.onCreate(filledOrder,BigDecimal.ZERO, sessionMode, context, plan, direction, purpose, parentOrderId, relatedHedgeId);
        TradeSession updatesSession = sessionService.addOrder(session.getId(), newOrder);
        log.error("open order {}", newOrder.getOrderId());
        return updatesSession;
    }

    private boolean waitForFilledOrder(Order order, int maxWaitMillis, int intervalMillis) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Order updated = orderService.getOrder(order.getOrderId());
            if (updated.getOrderStatus().equals(OrderStatus.FILLED)) {
                log.info("✅ Order {} is filled", order.getOrderId());
                return true;
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("⚠️ Order {} not filled after {}ms", order.getOrderId(), maxWaitMillis);
        return false;
    }
}

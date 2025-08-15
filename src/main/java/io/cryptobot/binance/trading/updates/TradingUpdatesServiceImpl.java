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
import io.cryptobot.configs.locks.TradeSessionLockRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingUpdatesServiceImpl implements TradingUpdatesService {
    private final TradePlanGetService tradePlanGetService;
    private final TradeSessionService sessionService;
    private final OrderService orderService;
    private final TradeSessionLockRegistry lockRegistry;

    @Override
    public TradeSession closePosition(TradeSession session, SessionMode sessionMode, Long idOrder, Long relatedHedgeId, TradingDirection direction, OrderPurpose purpose, BigDecimal currentPrice, String context) {
        ReentrantLock lock = lockRegistry.getLock(session.getId());
        lock.lock();
        try {
            TradePlan tradePlan = tradePlanGetService.getPlan(session.getTradePlan());
            TradeOrder entryOrder = session.getOrders().stream()
                    .filter(o -> o.getOrderId().equals(idOrder))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Order not found: " + idOrder));

            BigDecimal entryPrice = entryOrder.getPrice();
            BigDecimal count = entryOrder.getCount();

            Order orderClosed = orderService.closeOrder(entryOrder);

            // Проверяем что ордер был создан успешно
            if (orderClosed == null) {
                log.warn("⚠️ Failed to create close order for orderId: {} (position may already be closed)", idOrder);
                return session;
            }

            //check filled
            boolean filled = waitForFilledOrder(orderClosed, 15000, 200);
            if (!filled) {
                log.warn("Order {} was not filled in time", orderClosed.getOrderId());
                return session;
            }

            Order filledOrder;
            try {
                filledOrder = orderService.getOrder(orderClosed.getOrderId());
                if (filledOrder == null) {
                    log.warn("⚠️ Failed to get filled order for orderId: {}", orderClosed.getOrderId());
                    return session;
                }
            } catch (Exception e) {
                log.error("❌ Error getting filled order for orderId {}: {}", orderClosed.getOrderId(), e.getMessage());
                return session;
            }

            //calc pnl
            BigDecimal pnlFraction = calcPnl(direction, filledOrder, entryPrice);
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
        } catch (Exception e) {
            log.error(e.toString());
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TradeSession openPosition(TradeSession session, SessionMode sessionMode, TradingDirection direction, OrderPurpose purpose, BigDecimal currentPrice, String context, Long parentOrderId, Long relatedHedgeId) {
        ReentrantLock lock = lockRegistry.getLock(session.getId());
        lock.lock();
        try {
            // if exists 2 orders -> not open new, last level validation
            if (purpose == OrderPurpose.HEDGE_OPEN && (session.isActiveLong() && session.isActiveShort())) {
                log.info("🛡️ Session {} skip HEDGE_OPEN: both directions already active", session.getId());
                return session;
            }

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
//        Order orderOpen = orderService.createOrder(coin, count.doubleValue(), side, true);
            Order orderOpen = orderService.createLimitOrElseMarket(coin, count.doubleValue(), side, plan.getSizes()); //check new
            boolean filled = waitForFilledOrder(orderOpen, 5000, 200);
            if (!filled) {
                log.warn("Order {} was not filled in time", orderOpen.getOrderId());
                return session;
            }
            Order filledOrder;
            try {
                filledOrder = orderService.getOrder(orderOpen.getOrderId());
                if (filledOrder == null) {
                    log.warn("⚠️ Failed to get filled order for orderId: {}", orderOpen.getOrderId());
                    return session;
                }
            } catch (Exception e) {
                log.error("❌ Error getting filled order for orderId {}: {}", orderOpen.getOrderId(), e.getMessage());
                return session;
            }

            TradeOrder newOrder = new TradeOrder();
            newOrder.onCreate(filledOrder, BigDecimal.ZERO, sessionMode, context, plan, direction, purpose, parentOrderId, relatedHedgeId);

            TradeSession updatesSession = sessionService.addOrder(session.getId(), newOrder);
            log.error("open order {}", newOrder.getOrderId());
            return updatesSession;
        } catch (Exception e) {
            log.error(e.toString());
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TradeSession openAveragePosition(TradeSession session, SessionMode sessionMode, TradingDirection direction, OrderPurpose purpose, BigDecimal currentPrice, String context, Long parentOrderId) {
        ReentrantLock lock = lockRegistry.getLock(session.getId());
        lock.lock();
        try {
            //todo test
            if (purpose == OrderPurpose.AVERAGING_OPEN) {
                boolean avgActiveThisDir = (direction == TradingDirection.LONG) ? session.isActiveAverageLong() : session.isActiveAverageShort();
                if (avgActiveThisDir) {
                    log.info("🛡️ Session {} skip AVERAGING_OPEN {}: averaging already active for this direction", session.getId(), direction);
                    return session;
                }
            }

            TradeOrder parentOrder = session.getOrders().stream()
                    .filter(o -> o.getOrderId().equals(parentOrderId))
                    .findFirst()
                    .orElse(null);
            if (parentOrder == null) return session;

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
//            Order orderOpen = orderService.createOrder(coin, count.doubleValue(), side, true);
            Order orderOpen = orderService.createLimitOrElseMarket(coin, count.doubleValue(), side, plan.getSizes()); //check new
            if (orderOpen == null) {
                log.warn("⚠️ Failed to create order for averaging");
                return session;
            }
            boolean filled = waitForFilledOrder(orderOpen, 5000, 200);
            if (!filled) {
                log.warn("Order {} was not filled in time", orderOpen.getOrderId());
                return session;
            }
            Order filledOrder;
            try {
                filledOrder = orderService.getOrder(orderOpen.getOrderId());
                if (filledOrder == null) {
                    log.warn("⚠️ Failed to get filled order for orderId: {}", orderOpen.getOrderId());
                    return session;
                }
            } catch (Exception e) {
                log.error("❌ Error getting filled order for orderId {}: {}", orderOpen.getOrderId(), e.getMessage());
                return session;
            }

            TradeOrder newOrder = new TradeOrder();
            newOrder.onCreateAverage(filledOrder, parentOrder, BigDecimal.ZERO, sessionMode, context, plan, direction, purpose);

            TradeSession updatesSession = sessionService.addOrder(session.getId(), newOrder);
            log.error("open order {}", newOrder.getOrderId());
            return updatesSession;
        } catch (Exception e) {
            log.error(e.toString());
            return null;
        } finally {
            lock.unlock();
        }
    }

    private boolean waitForFilledOrder(Order order, int maxWaitMillis, int intervalMillis) {
        if (order == null) {
            log.warn("⚠️ Cannot wait for null order");
            return false;
        }

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            try {
                Order updated = orderService.getOrder(order.getOrderId());
                if (updated != null && updated.getOrderStatus() != null && updated.getOrderStatus().equals(OrderStatus.FILLED)) {
                    log.info("✅ Order {} is filled", order.getOrderId());
                    return true;
                }
            } catch (Exception e) {
                log.warn("⚠️ Error getting order status for {}: {}", order.getOrderId(), e.getMessage());
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

    private BigDecimal calcPnl(TradingDirection direction, Order filledOrder, BigDecimal entryPrice) {
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
        return pnlFraction;
    }
}
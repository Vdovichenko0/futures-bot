package io.cryptobot.binance.order.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.binance.order.dao.OrderRepository;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.mapper.OrderMapper;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;

import io.cryptobot.helpers.OrderHelper;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final Ticker24hService ticker24hService;
    private final DepthService depthService;
    private final UMFuturesClientImpl client;

    //    @Scheduled(initialDelay = 30_000)
    @Transactional
    public void init() {
        try {
            log.info("🚀 Starting order creation and closure test...");

            Order order = createOrder("BTCUSDT", 0.001, OrderSide.BUY, true);
            if (order == null) {
                log.error("❌ Failed to create order");
                return;
            }

            log.info("✅ Order created successfully: {}", order.getOrderId());

            log.info("⏳ Waiting 60 seconds before closing order...");
            Thread.sleep(60_000);

            Order updatedOrder = getOrderFromBinance(order.getOrderId(), order.getSymbol());
            if (updatedOrder != null && !updatedOrder.getOrderStatus().equals(OrderStatus.NEW)) {
                Order orderHandge = createOrder("BTCUSDT", 0.001, OrderSide.SELL, true);
                log.info("📊 Updated order info: {}", updatedOrder);
//                Order closedOrder = closeOrder(updatedOrder);
                if (orderHandge != null) {
                    log.info("✅ Order closed successfully: {}", orderHandge.getOrderId());
                } else {
                    log.error("❌ Failed to close order: {}", updatedOrder.getOrderId());
                }
            } else {
                log.error("❌ Failed to get updated order info for: {}", order.getOrderId());
            }

        } catch (InterruptedException e) {
            log.error("❌ Process interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ Error in init process", e);
        }
    }

//    @Scheduled(initialDelay = 30_000)
    @Transactional
    public void initLimit() {
       createLimitOrElseMarket("LINKUSDC", 0.05, OrderSide.BUY, SizeModel.builder().tickSize(BigDecimal.ONE).build());
//        BigDecimal ask = depthService.getNearestAskPrice("SOLUSDC"); //long
//        BigDecimal bid = depthService.getNearestBidPrice("SOLUSDC"); //short
        BigDecimal ask = depthService.getAskPriceAbove("LINKUSDC", 1); //long //1 like 2(array index from 0)
        BigDecimal bid = depthService.getBidPriceBelow("LINKUSDC", 1); //short
        log.info("ask {} || bid {}", ask, bid);

    }

    @Override
    @Transactional
    public void updateOrder(Order updatedOrder) {
        if (updatedOrder == null || updatedOrder.getOrderId() == null) {
            log.warn("⚠️ Updated order or orderId is null, skipping");
            return;
        }

        final int maxRetries = 5;
        long delayMs = 100;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            try {
                Order existingOrder = orderRepository.findById(updatedOrder.getOrderId()).orElse(null);
                if (existingOrder == null) {
                    log.debug("Order {} not found in DB (attempt {}/{})", updatedOrder.getOrderId(), attempt, maxRetries);
                    if (attempt >= maxRetries) {
                        log.warn("⚠️ Order with ID {} still not found after {} attempts, giving up", updatedOrder.getOrderId(), attempt);
                        return;
                    }
                    Thread.sleep(delayMs);
                    delayMs = Math.min(1600, delayMs * 2);
                    continue;
                }

                StringBuilder changes = new StringBuilder();
                boolean changed = OrderHelper.mergeAndDetectChanges(existingOrder, updatedOrder, changes);

                if (changed) {
                    log.debug("🔄 Updating order {}: {}", existingOrder.getOrderId(), changes);
                    log.debug("Before saving: cumulativeFilledQty={}, realizedPnl={}", existingOrder.getCumulativeFilledQty(), existingOrder.getRealizedPnl());
                    orderRepository.save(existingOrder);
                } else {
                    log.debug("✅ Order {} already up to date.", existingOrder.getOrderId());
                }
                return;

            } catch (Exception e) {
                log.error("❌ Error updating order (attempt {}/{}): {}", attempt, maxRetries, e.getMessage(), e);
                if (attempt >= maxRetries) {
                    log.error("💥 Order update failed after {} attempts.", maxRetries);
                    return;
                }
                try {
                    Thread.sleep(Math.min(3200, delayMs));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while backing off", ie);
                    return;
                }
                delayMs *= 2;
            }
        }
    }

    @Override
    @Transactional
    public Order createOrder(String symbol, Double amount, OrderSide side, Boolean hedgeMode) {
        try {
            log.info("🚀 Creating order: symbol={}, amount={}, side={}, hedgeMode={}", symbol, amount, side, hedgeMode);

            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol.toUpperCase());
            parameters.put("side", side.toString());
            parameters.put("type", "MARKET");
            parameters.put("quantity", amount);
            // Убираем timeInForce для MARKET ордеров - Binance API не требует этот параметр

            if (Boolean.TRUE.equals(hedgeMode)) {
                // В hedge model нужно явно указывать, какую сторону позицию открываешь
                // если side == BUY → LONG, если SELL → SHORT
                String positionSide = side.equals(OrderSide.BUY) ? "LONG" : "SHORT";
                parameters.put("positionSide", positionSide);

                log.info("🔧 Opening position (hedge mode): symbol={}, side={}, positionSide={}, quantity={}", symbol, side, positionSide, amount);
            } else {
                parameters.remove("positionSide");
                log.info("🔧 Opening position (non-hedge): symbol={}, side={}, quantity={}", symbol, side, amount);
            }

            log.info("📤 Sending market order request: {}", parameters);
            String result = client.account().newOrder(parameters);

            log.info("📥 Market order response from Binance: {}", result);

            JsonNode jsonNode = objectMapper.readTree(result);
            Order order = OrderMapper.fromRest(jsonNode);

            log.info("✅ Market order created and mapped successfully: orderId={}, status={}, filled={}, avgPrice={}", 
                order.getOrderId(), order.getOrderStatus(), 
                order.getCumulativeFilledQty(), order.getAveragePrice());
            
            orderRepository.save(order);
            return order;

        } catch (Exception e) {
            log.error("❌ Failed to create market order: symbol={}, amount={}, side={}, hedgeMode={}", symbol, amount, side, hedgeMode, e);
            return null;
        }
    }

    @Override
    @Transactional
    public Order createLimitOrElseMarket(String symbol, Double amount, OrderSide side, SizeModel sizes) {
        // локальные пороги
        final int N_TICKS = 5;
        final BigDecimal DRIFT_PCT = new BigDecimal("0.1");   // 0.1%
        final long REPRICE_AFTER_MS = 5_000L;
        final long MAX_TIME_MS = 30_000L;
        final int POLL_MS = 1_000;

        try {
            log.info("🚀 Starting createLimitOrElseMarket: symbol={}, amount={}, side={}", symbol, amount, side);
            
            final long tStart = System.currentTimeMillis();
            BigDecimal anchor = ticker24hService.getPrice(symbol); // last tick price
            log.info("📊 Initial anchor price: {} for symbol: {}", anchor, symbol);

            // limit price from depth
            BigDecimal limitPrice = (side == OrderSide.BUY)
                    ? depthService.getBidPriceBelow(symbol, N_TICKS)   // maker BUY: bid или ниже
                    : depthService.getAskPriceAbove(symbol, N_TICKS);  // maker SELL: ask или выше
            
            log.info("💰 Calculated limit price: {} (side={}, N_TICKS={})", limitPrice, side, N_TICKS);

            Long orderId = placeLimit(symbol, amount, side, limitPrice);
            if (orderId == null) {
                log.error("❌ Failed to place limit order, returning null");
                return null;
            }
            
            log.info("✅ Limit order placed successfully: orderId={}, price={}", orderId, limitPrice);

            long lastRepriceAt = System.currentTimeMillis();

            while (System.currentTimeMillis() - tStart < MAX_TIME_MS) {
                Thread.sleep(POLL_MS);

                Order od = getOrderFromBinance(orderId, symbol);
                if (od == null) {
                    log.warn("⚠️ Could not retrieve order from Binance: orderId={}", orderId);
                    continue;
                }
                
                log.info("📋 Order status check: orderId={}, status={}, filled={}, remaining={}", 
                    orderId, od.getOrderStatus(), 
                    od.getCumulativeFilledQty() != null ? od.getCumulativeFilledQty() : "null",
                    od.getCumulativeFilledQty() != null ? BigDecimal.valueOf(amount).subtract(od.getCumulativeFilledQty()) : "null");
                
                if (od.getOrderStatus() == OrderStatus.FILLED) {
                    log.info("🎉 Order fully filled: orderId={}", orderId);
                    return od;
                }

                BigDecimal filled = od.getCumulativeFilledQty() != null ? od.getCumulativeFilledQty() : BigDecimal.ZERO;
                BigDecimal remaining = BigDecimal.valueOf(amount).subtract(filled).max(BigDecimal.ZERO);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    log.info("✅ Order completed (remaining <= 0): orderId={}, filled={}", orderId, filled);
                    return od;
                }

                // if price > 0.1 -> market order
                BigDecimal curTick = ticker24hService.getPrice(symbol);
                log.info("📈 Current tick price: {}, anchor price: {}", curTick, anchor);
                
                if (anchor.signum() > 0) {
                    BigDecimal driftPct = curTick.subtract(anchor).abs()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(anchor, 8, RoundingMode.HALF_UP);
                    
                    log.info("📊 Price drift calculation: current={}, anchor={}, driftPct={}%, threshold={}%", 
                        curTick, anchor, driftPct, DRIFT_PCT);
                    
                    if (driftPct.compareTo(DRIFT_PCT) >= 0) {
                        log.warn("⚠️ Price drift exceeded threshold! Drift: {}% >= {}%, cancelling limit order and switching to market", 
                            driftPct, DRIFT_PCT);
                        cancelOrderSilently(symbol, orderId);
                        log.info("🔄 Creating market order for remaining amount: {}", remaining);
                        return createOrder(symbol, remaining.doubleValue(), side, true);
                    }
                }

                // recreate order or if order is not filled
                long timeSinceLastReprice = System.currentTimeMillis() - lastRepriceAt;
                boolean shouldReprice = timeSinceLastReprice >= REPRICE_AFTER_MS
                        || od.getOrderStatus() == OrderStatus.CANCELED
                        || od.getOrderStatus() == OrderStatus.EXPIRED
                        || od.getOrderStatus() == OrderStatus.REJECTED;
                
                if (shouldReprice) {
                    log.info("🔄 Repricing order: timeSinceLastReprice={}ms, status={}, shouldReprice={}", 
                        timeSinceLastReprice, od.getOrderStatus(), shouldReprice);
                    
                    cancelOrderSilently(symbol, orderId);

                    BigDecimal newPrice = (side == OrderSide.BUY)
                            ? depthService.getBidPriceBelow(symbol, N_TICKS)   // BUY → bidBelow (maker)
                            : depthService.getAskPriceAbove(symbol, N_TICKS);  // SELL → askAbove (maker)

                    
                    log.info("💰 New limit price: {} (previous: {})", newPrice, limitPrice);

                    orderId = placeLimit(symbol, remaining.doubleValue(), side, newPrice);
                    if (orderId == null) {
                        log.error("❌ Failed to place new limit order, switching to market order");
                        return createOrder(symbol, remaining.doubleValue(), side, true);
                    }
                    
                    log.info("✅ New limit order placed: orderId={}, price={}, remaining={}", orderId, newPrice, remaining);

                    anchor = curTick;                 // new last tick
                    lastRepriceAt = System.currentTimeMillis();
                    log.info("📊 Updated anchor price: {}", anchor);
                }
            }

            // timeout 30 sec, if we dont have limit filled - create market
            log.warn("⏰ Timeout reached ({}ms), checking final order status", MAX_TIME_MS);
            
            Order last = getOrderFromBinance(orderId, symbol);
            BigDecimal filled = last != null && last.getCumulativeFilledQty() != null ? last.getCumulativeFilledQty() : BigDecimal.ZERO;
            BigDecimal remaining = BigDecimal.valueOf(amount).subtract(filled).max(BigDecimal.ZERO);
            
            log.info("📋 Final order status: orderId={}, filled={}, remaining={}", orderId, filled, remaining);
            
            cancelOrderSilently(symbol, orderId);
            
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                log.info("🔄 Creating final market order for remaining amount: {}", remaining);
                return createOrder(symbol, remaining.doubleValue(), side, true);
            } else {
                log.info("✅ Order completed via timeout handling: {}", last);
                return last;
            }

        } catch (Exception e) {
            log.error("❌ createLimitOrElseMarket error: symbol={}, amount={}, side={}", symbol, amount, side, e);
            return null;
        }
    }

    private BigDecimal buildPriceLimit(OrderSide side, BigDecimal tickSize, BigDecimal price) {
        final BigDecimal OFFSET = new BigDecimal("0.001");
        BigDecimal shifted = (side == OrderSide.BUY)
                ? price.multiply(BigDecimal.ONE.add(OFFSET))   // LONG → +0.1%
                : price.multiply(BigDecimal.ONE.subtract(OFFSET)); // SHORT → -0.1%

        return quantizeToTick(shifted, tickSize, side == OrderSide.BUY ? RoundingMode.CEILING : RoundingMode.FLOOR);
    }

    private static BigDecimal quantizeToTick(BigDecimal price, BigDecimal tickSize, RoundingMode mode) {
        BigDecimal steps = price.divide(tickSize, 0, mode);
        return steps.multiply(tickSize).stripTrailingZeros();
    }

    @Override
    @Transactional // todo count, not full model
    public Order closeOrder(Order order) {
        try {
            log.info("Closing order/position: {}", order);

            // Проверяем статус ордера
            if (order.getOrderStatus().equals(OrderStatus.NEW) || order.getOrderStatus().equals(OrderStatus.CANCELED)) {
                log.info("Order {} is not fully filled (status: {}), cancelling it", order.getOrderId(), order.getOrderStatus());
                return null;
            }

            // Если ордер исполнен, закрываем позицию
            BigDecimal qtyToClose = order.getCumulativeFilledQty();
            if (qtyToClose == null || qtyToClose.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Quantity to close is zero or missing for order: {}", order);
                return null;
            }

            // Определяем сторону закрытия: если была BUY (лонг) — закрываем SELL, если SELL (шорт) — BUY
            OrderSide closingSide = order.getSide().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY;

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", order.getSymbol().toUpperCase());
            params.put("side", closingSide.toString());
            params.put("type", "MARKET");
            params.put("quantity", qtyToClose.toPlainString());
//            params.put("reduceOnly", "true");

            // В hedge model нужно указать positionSide точно таким же, как у исходной позиции (LONG/SHORT)
            if (order.getPositionSide() != null && !order.getPositionSide().isBlank()) {
                params.put("positionSide", order.getPositionSide());
            }

            String response = client.account().newOrder(params);
            log.info("Close order response: {}", response);

            JsonNode node = objectMapper.readTree(response);
            Order closed = OrderMapper.fromRest(node);
            orderRepository.save(closed);
            log.info("Mapped and saved closed order: {}", closed);
            return closed;

        } catch (Exception e) {
            log.error("Failed to close order: {}", order, e);
            return null;
        }
    }

    @Override
    @Transactional
    public Order closeOrder(BigDecimal count, OrderSide closingSide, String symbol, TradingDirection direction) {
        try {

            // Если ордер исполнен, закрываем позицию
            if (count == null || count.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Quantity to close is zero or missing for order: {}", symbol);
                return null;
            }

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol.toUpperCase());
            params.put("side", closingSide.toString());
            params.put("type", "MARKET");
            params.put("quantity", count.toPlainString());

            // В hedge model нужно указать positionSide точно таким же, как у исходной позиции (LONG/SHORT)
            // TradeOrder.direction уже в правильном формате (LONG/SHORT)
            String positionSide = direction.toString();
            params.put("positionSide", positionSide);

            log.info("🔧 Closing position: symbol={}, side={}, positionSide={}, quantity={}", symbol, closingSide, positionSide, count);

            String response = client.account().newOrder(params);
            log.info("Close order response: {}", response);

            JsonNode node = objectMapper.readTree(response);
            Order closed = OrderMapper.fromRest(node);
            orderRepository.save(closed);
            return closed;

        } catch (Exception e) {
            log.error("Failed to close order: {}", symbol, e);
            // Обработка специфической ошибки Binance -2022 (ReduceOnly Order is rejected)
            if (e.getMessage() != null && e.getMessage().contains("-2022")) {
                log.warn("⚠️ Position already closed or doesn't exist (error -2022) for order: {}", symbol);
                return null;
            }
            return null;
        }
    }

    @Override
    @Transactional
    public Order closeOrder(TradeOrder order) {
        try {
            log.info("Closing order/position: {}", order);

            // Если ордер исполнен, закрываем позицию
            BigDecimal qtyToClose = order.getCount();
            if (qtyToClose == null || qtyToClose.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Quantity to close is zero or missing for order: {}", order);
                return null;
            }

            // Определяем сторону закрытия: если была BUY (лонг) — закрываем SELL, если SELL (шорт) — BUY
            OrderSide closingSide = order.getSide().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY;

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", order.getSymbol().toUpperCase());
            params.put("side", closingSide.toString());
            params.put("type", "MARKET");
            params.put("quantity", qtyToClose.toPlainString());

            // В hedge model нужно указать positionSide точно таким же, как у исходной позиции (LONG/SHORT)
            // TradeOrder.direction уже в правильном формате (LONG/SHORT)
            String positionSide = order.getDirection().toString();
            params.put("positionSide", positionSide);

            log.info("🔧 Closing position: symbol={}, side={}, positionSide={}, quantity={}",
                    order.getSymbol(), closingSide, positionSide, qtyToClose);


            String response = client.account().newOrder(params);
            log.info("Close order response: {}", response);

            JsonNode node = objectMapper.readTree(response);
            Order closed = OrderMapper.fromRest(node);
            orderRepository.save(closed);
            log.info("Mapped and saved closed order: {}", closed);
            return closed;

        } catch (Exception e) {
            log.error("Failed to close order: {}", order, e);

            // Обработка специфической ошибки Binance -2022 (ReduceOnly Order is rejected)
            if (e.getMessage() != null && e.getMessage().contains("-2022")) {
                log.warn("⚠️ Position already closed or doesn't exist (error -2022) for order: {}", order.getOrderId());
                // Возвращаем null чтобы TradingUpdatesService мог обработать это
                return null;
            }

            return null;
        }
    }

    @Override
    @Transactional
    public Order getOrder(Long idOrder) {
        return orderRepository.findById(idOrder).orElse(null);
    }

    @Override
    @Transactional
    public Order getOrderFromBinance(Long idOrder, String symbol) {
        try {
            log.info("🔍 Retrieving order from Binance: orderId={}, symbol={}", idOrder, symbol);
            
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("orderId", idOrder);
            parameters.put("symbol", symbol);

            log.info("📤 Sending order query request: {}", parameters);
            String result = client.account().queryOrder(parameters);

            log.info("📥 Received order data from Binance: {}", result);

            JsonNode jsonNode = objectMapper.readTree(result);
            Order order = OrderMapper.fromRest(jsonNode);
            
            log.info("✅ Order retrieved and mapped successfully: orderId={}, status={}, filled={}, price={}", 
                order.getOrderId(), order.getOrderStatus(), 
                order.getCumulativeFilledQty(), order.getPrice());
            
            return order;

        } catch (Exception e) {
            log.error("❌ Failed to get order from Binance: orderId={}, symbol={}", idOrder, symbol, e);
            return null;
        }
    }

    private Long placeLimit(String symbol, Double amount, OrderSide side, BigDecimal price) {
        for (int i = 0; i < 3; i++) {
            try {

                LinkedHashMap<String, Object> p = new LinkedHashMap<>();
                p.put("symbol", symbol.toUpperCase());
                p.put("side", side.toString());
                p.put("type", "LIMIT");
                p.put("timeInForce", "GTX"); // maker-only
                p.put("quantity", amount);
                p.put("price", price.toPlainString());
                p.put("positionSide", side == OrderSide.BUY ? "LONG" : "SHORT");

                String res = client.account().newOrder(p);
                Order order = OrderMapper.fromRest(objectMapper.readTree(res));
                orderRepository.save(order);
                return order.getOrderId();
            } catch (com.binance.connector.futures.client.exceptions.BinanceClientException e) {
                if (e.getMessage() != null && e.getMessage().contains("\"code\":-5022")) {
                    BigDecimal rawAgain = (side == OrderSide.BUY)
                            ? depthService.getBidPriceBelow(symbol, 2)
                            : depthService.getAskPriceAbove(symbol, 2);
                    price = rawAgain != null ? rawAgain : price;
                    continue;
                }
                throw e;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }


    private void cancelOrderSilently(String symbol, Long orderId) {
        try {
            log.info("🚫 Cancelling order: symbol={}, orderId={}", symbol, orderId);
            
            LinkedHashMap<String, Object> p = new LinkedHashMap<>();
            p.put("symbol", symbol.toUpperCase());
            p.put("orderId", orderId);
            
            log.info("📤 Sending cancel order request: {}", p);
            client.account().cancelOrder(p);
            
            log.info("✅ Order cancelled successfully: symbol={}, orderId={}", symbol, orderId);
        } catch (Exception e) {
            log.warn("⚠️ cancelOrderSilently warning: symbol={}, orderId={}, error={}", symbol, orderId, e.getMessage());
        }
    }
}
package io.cryptobot.binance.order.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.binance.order.dao.OrderRepository;
import io.cryptobot.binance.order.mapper.OrderMapper;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.configs.service.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import io.cryptobot.helpers.OrderHelper;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService{
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(initialDelay = 30_000)
    @Transactional
    public void init() {
        try {
            log.info("🚀 Starting order creation and closure test...");
            
            Order order = createOrder("BTCUSDT", 0.001, "BUY", true);
            if (order == null) {
                log.error("❌ Failed to create order");
                return;
            }
            
            log.info("✅ Order created successfully: {}", order.getOrderId());
            
            log.info("⏳ Waiting 60 seconds before closing order...");
            Thread.sleep(60_000);

            Order updatedOrder = getOrderFromBinance(order.getOrderId(), order.getSymbol());
            if (updatedOrder != null && !updatedOrder.getOrderStatus().equalsIgnoreCase("NEW")) {
                Order orderHandge = createOrder("BTCUSDT", 0.001, "SELL", true);
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
                        log.warn("⚠️ Order with ID {} still not found after {} attempts, giving up", updatedOrder.getOrderId(), maxRetries);
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
    public Order createOrder(String symbol, Double amount, String side, Boolean hedgeMode) {
        try {
            log.info("Creating order: symbol={}, amount={}, side={}, hedgeMode={}", symbol, amount, side, hedgeMode);

            UMFuturesClientImpl client = new UMFuturesClientImpl(AppConfig.API_KEY, AppConfig.SECRET_KEY, AppConfig.BINANCE_URL);

            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol.toUpperCase());
            parameters.put("side", side.toUpperCase());
            parameters.put("type", "MARKET");
            parameters.put("quantity", amount);
//            parameters.put("timeInForce", "GTC"); // Good Till Cancel

            if (Boolean.TRUE.equals(hedgeMode)) {
                // В hedge mode нужно явно указывать, какую сторону позицию открываешь
                // если side == BUY → LONG, если SELL → SHORT
                String positionSide = "BUY".equalsIgnoreCase(side) ? "LONG" : "SHORT";
                parameters.put("positionSide", positionSide);
            } else {
                parameters.remove("positionSide");
            }

            String result = client.account().newOrder(parameters);

            log.info("Order created successfully: {}", result);

            JsonNode jsonNode = objectMapper.readTree(result);
            Order order = OrderMapper.fromRest(jsonNode);

            log.info("Order mapped successfully: {}", order);
            orderRepository.save(order);
            return order;

        } catch (Exception e) {
            log.error("Failed to create order: symbol={}, amount={}, side={}", symbol, amount, side, e);
            return null;
        }
    }

    @Override
    @Transactional // todo count, not full model
    public Order closeOrder(Order order) {
        try {
            log.info("Closing order/position: {}", order);

            UMFuturesClientImpl client = new UMFuturesClientImpl(
                    AppConfig.API_KEY,
                    AppConfig.SECRET_KEY,
                    AppConfig.BINANCE_URL
            );

            // Проверяем статус ордера
            if ("NEW".equals(order.getOrderStatus()) || "PARTIALLY_FILLED".equals(order.getOrderStatus())) {
                log.info("Order {} is not fully filled (status: {}), cancelling it", order.getOrderId(), order.getOrderStatus());
//                return cancelOrder(order);
                return null; //todo
            }

            // Если ордер исполнен, закрываем позицию
            BigDecimal qtyToClose = order.getCumulativeFilledQty();
            if (qtyToClose == null || qtyToClose.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Quantity to close is zero or missing for order: {}", order);
                return null;
            }

            // Определяем сторону закрытия: если была BUY (лонг) — закрываем SELL, если SELL (шорт) — BUY
            String closingSide = "BUY".equalsIgnoreCase(order.getSide()) ? "SELL" : "BUY";

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", order.getSymbol().toUpperCase());
            params.put("side", closingSide);
            params.put("type", "MARKET");
            params.put("quantity", qtyToClose.toPlainString());
//            params.put("reduceOnly", "true");

            // В hedge mode нужно указать positionSide точно таким же, как у исходной позиции (LONG/SHORT)
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
    public Order getOrder(Long idOrder) {
        return orderRepository.findById(idOrder).orElse(null);
    }

    @Override
    @Transactional
    public Order getOrderFromBinance(Long idOrder, String symbol) {
        try {

            UMFuturesClientImpl client = new UMFuturesClientImpl(AppConfig.API_KEY, AppConfig.SECRET_KEY, AppConfig.BINANCE_URL);

            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("orderId", idOrder);
            parameters.put("symbol", symbol);

            String result = client.account().queryOrder(parameters);

            log.info("Order retrieved successfully from Binance");
            log.info(result);

            JsonNode jsonNode = objectMapper.readTree(result);
            Order order = OrderMapper.fromRest(jsonNode);
            log.info("Order mapped successfully: {}", order);
            return order;

        } catch (Exception e) {
            log.error("Failed to get order from Binance with ID: {}", idOrder, e);
            return null;
        }
    }
}
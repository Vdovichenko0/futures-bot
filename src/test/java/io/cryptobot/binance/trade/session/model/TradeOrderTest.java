package io.cryptobot.binance.trade.session.model;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradeOrder Tests")
class TradeOrderTest {

    @Test
    @DisplayName("Should create TradeOrder with valid data")
    void testCreateTradeOrder() {
        // Given
        TradeOrder tradeOrder = TradeOrder.builder()
                .orderId(123456789L)
                .creationContext("Test context")
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .type("MARKET")
                .count(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .amount(new BigDecimal("50.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .status(OrderStatus.FILLED)
                .pnl(new BigDecimal("10.50"))
                .leverage(10)
                .parentOrderId(null)
                .relatedHedgeId(null)
                .modeAtCreation(SessionMode.SCALPING)
                .orderTime(LocalDateTime.now())
                .pnlHigh(new BigDecimal("15.00"))
                .trailingActive(false)
                .basePnl(new BigDecimal("5.00"))
                .maxChangePnl(new BigDecimal("10.00"))
                .build();

        // When & Then
        assertNotNull(tradeOrder);
        assertEquals(123456789L, tradeOrder.getOrderId());
        assertEquals("Test context", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.MAIN_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("BTCUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(new BigDecimal("0.001"), tradeOrder.getCount());
        assertEquals(new BigDecimal("50000.00"), tradeOrder.getPrice());
        assertEquals(new BigDecimal("50.00"), tradeOrder.getAmount());
        assertEquals(new BigDecimal("0.05"), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(new BigDecimal("10.50"), tradeOrder.getPnl());
        assertEquals(10, tradeOrder.getLeverage());
        assertNull(tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertEquals(SessionMode.SCALPING, tradeOrder.getModeAtCreation());
        assertNotNull(tradeOrder.getOrderTime());
        assertEquals(new BigDecimal("15.00"), tradeOrder.getPnlHigh());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(new BigDecimal("5.00"), tradeOrder.getBasePnl());
        assertEquals(new BigDecimal("10.00"), tradeOrder.getMaxChangePnl());
    }

    @Test
    @DisplayName("Should create TradeOrder with default values")
    void testCreateTradeOrderWithDefaults() {
        // Given
        TradeOrder tradeOrder = new TradeOrder();

        // When & Then
        assertNotNull(tradeOrder);
        assertEquals(BigDecimal.ZERO, tradeOrder.getAmount());
        assertEquals(BigDecimal.ZERO, tradeOrder.getCommission());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnl());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertFalse(tradeOrder.getTrailingActive());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
    }

    @Test
    @DisplayName("Should set and get PnL tracking fields")
    void testSetAndGetPnlTrackingFields() {
        // Given
        TradeOrder tradeOrder = new TradeOrder();
        BigDecimal basePnl = new BigDecimal("5.00");
        BigDecimal maxChangePnl = new BigDecimal("10.00");

        // When
        tradeOrder.setBasePnl(basePnl);
        tradeOrder.setMaxChangePnl(maxChangePnl);

        // Then
        assertEquals(basePnl, tradeOrder.getBasePnl());
        assertEquals(maxChangePnl, tradeOrder.getMaxChangePnl());
    }

    @Test
    @DisplayName("Should set and get trailing fields")
    void testSetAndGetTrailingFields() {
        // Given
        TradeOrder tradeOrder = new TradeOrder();
        BigDecimal pnlHigh = new BigDecimal("15.00");

        // When
        tradeOrder.setPnlHigh(pnlHigh);
        tradeOrder.setTrailingActive(true);

        // Then
        assertEquals(pnlHigh, tradeOrder.getPnlHigh());
        assertTrue(tradeOrder.getTrailingActive());
    }

    @Test
    @DisplayName("Should create TradeOrder from Order with onCreate")
    void testOnCreateFromOrder() {
        // Given
        Order order = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("50000.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradePlan tradePlan = TradePlan.builder()
                .leverage(10)
                .build();

        BigDecimal pnl = new BigDecimal("10.50");
        SessionMode sessionMode = SessionMode.SCALPING;
        String context = "Test creation context";
        TradingDirection direction = TradingDirection.LONG;
        OrderPurpose purpose = OrderPurpose.MAIN_OPEN;
        Long parentOrderId = null;
        Long relatedHedgeId = null;

        TradeOrder tradeOrder = new TradeOrder();

        // When
        tradeOrder.onCreate(order, pnl, sessionMode, context, tradePlan, direction, purpose, parentOrderId, relatedHedgeId);

        // Then
        assertEquals(123456789L, tradeOrder.getOrderId());
        assertEquals("Test creation context", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.MAIN_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("BTCUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(new BigDecimal("0.001"), tradeOrder.getCount());
        assertEquals(new BigDecimal("50000.00"), tradeOrder.getPrice());
        assertEquals(new BigDecimal("0.05"), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(new BigDecimal("10.50"), tradeOrder.getPnl());
        assertEquals(10, tradeOrder.getLeverage());
        assertEquals(SessionMode.SCALPING, tradeOrder.getModeAtCreation());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(tradeOrder.getAmount())); // 0.001 * 50000.00
        assertNull(tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
        assertNotNull(tradeOrder.getOrderTime());
    }

    @Test
    @DisplayName("Should create TradeOrder with hedge relationships")
    void testOnCreateWithHedgeRelationships() {
        // Given
        Order order = Order.builder()
                .orderId(987654321L)
                .symbol("ETHUSDT")
                .side(OrderSide.SELL)
                .orderType("LIMIT")
                .quantity(new BigDecimal("0.01"))
                .averagePrice(new BigDecimal("3000.00"))
                .commission(new BigDecimal("0.03"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradePlan tradePlan = TradePlan.builder()
                .leverage(20)
                .build();

        BigDecimal pnl = new BigDecimal("-5.25");
        SessionMode sessionMode = SessionMode.HEDGING;
        String context = "Hedge creation context";
        TradingDirection direction = TradingDirection.SHORT;
        OrderPurpose purpose = OrderPurpose.HEDGE_OPEN;
        Long parentOrderId = 123456789L;
        Long relatedHedgeId = null;

        TradeOrder tradeOrder = new TradeOrder();

        // When
        tradeOrder.onCreate(order, pnl, sessionMode, context, tradePlan, direction, purpose, parentOrderId, relatedHedgeId);

        // Then
        assertEquals(987654321L, tradeOrder.getOrderId());
        assertEquals("Hedge creation context", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.HEDGE_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.SHORT, tradeOrder.getDirection());
        assertEquals("ETHUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.SELL, tradeOrder.getSide());
        assertEquals("LIMIT", tradeOrder.getType());
        assertEquals(new BigDecimal("0.01"), tradeOrder.getCount());
        assertEquals(new BigDecimal("3000.00"), tradeOrder.getPrice());
        assertEquals(new BigDecimal("0.03"), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(new BigDecimal("-5.25"), tradeOrder.getPnl());
        assertEquals(20, tradeOrder.getLeverage());
        assertEquals(SessionMode.HEDGING, tradeOrder.getModeAtCreation());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(0, new BigDecimal("30.00").compareTo(tradeOrder.getAmount())); // 0.01 * 3000.00
        assertEquals(123456789L, tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
        assertNotNull(tradeOrder.getOrderTime());
    }

    @Test
    @DisplayName("Should create TradeOrder with hedge close relationship")
    void testOnCreateWithHedgeCloseRelationship() {
        // Given
        Order order = Order.builder()
                .orderId(555666777L)
                .symbol("ADAUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("100"))
                .averagePrice(new BigDecimal("0.50"))
                .commission(new BigDecimal("0.25"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradePlan tradePlan = TradePlan.builder()
                .leverage(5)
                .build();

        BigDecimal pnl = new BigDecimal("25.75");
        SessionMode sessionMode = SessionMode.HEDGING;
        String context = "Hedge close context";
        TradingDirection direction = TradingDirection.LONG;
        OrderPurpose purpose = OrderPurpose.HEDGE_CLOSE;
        Long parentOrderId = 123456789L;
        Long relatedHedgeId = 987654321L;

        TradeOrder tradeOrder = new TradeOrder();

        // When
        tradeOrder.onCreate(order, pnl, sessionMode, context, tradePlan, direction, purpose, parentOrderId, relatedHedgeId);

        // Then
        assertEquals(555666777L, tradeOrder.getOrderId());
        assertEquals("Hedge close context", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.HEDGE_CLOSE, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("ADAUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(new BigDecimal("100"), tradeOrder.getCount());
        assertEquals(new BigDecimal("0.50"), tradeOrder.getPrice());
        assertEquals(new BigDecimal("0.25"), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(new BigDecimal("25.75"), tradeOrder.getPnl());
        assertEquals(5, tradeOrder.getLeverage());
        assertEquals(SessionMode.HEDGING, tradeOrder.getModeAtCreation());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(new BigDecimal("50.00"), tradeOrder.getAmount()); // 100 * 0.50
        assertEquals(123456789L, tradeOrder.getParentOrderId());
        assertEquals(987654321L, tradeOrder.getRelatedHedgeId());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
        assertNotNull(tradeOrder.getOrderTime());
    }

    @Test
    @DisplayName("Should handle zero values in onCreate")
    void testOnCreateWithZeroValues() {
        // Given
        Order order = Order.builder()
                .orderId(111222333L)
                .symbol("DOTUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .commission(BigDecimal.ZERO)
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.NEW)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradePlan tradePlan = TradePlan.builder()
                .leverage(1)
                .build();

        BigDecimal pnl = BigDecimal.ZERO;
        SessionMode sessionMode = SessionMode.SCALPING;
        String context = "Zero values test";
        TradingDirection direction = TradingDirection.LONG;
        OrderPurpose purpose = OrderPurpose.MAIN_OPEN;
        Long parentOrderId = null;
        Long relatedHedgeId = null;

        TradeOrder tradeOrder = new TradeOrder();

        // When
        tradeOrder.onCreate(order, pnl, sessionMode, context, tradePlan, direction, purpose, parentOrderId, relatedHedgeId);

        // Then
        assertEquals(111222333L, tradeOrder.getOrderId());
        assertEquals("Zero values test", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.MAIN_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("DOTUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(BigDecimal.ZERO, tradeOrder.getCount());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPrice());
        assertEquals(BigDecimal.ZERO, tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnl());
        assertEquals(1, tradeOrder.getLeverage());
        assertEquals(SessionMode.SCALPING, tradeOrder.getModeAtCreation());
        assertEquals(OrderStatus.NEW, tradeOrder.getStatus());
        assertEquals(BigDecimal.ZERO, tradeOrder.getAmount()); // 0 * 0
        assertNull(tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
        assertNotNull(tradeOrder.getOrderTime());
    }

    @Test
    @DisplayName("Should handle large decimal values in onCreate")
    void testOnCreateWithLargeDecimalValues() {
        // Given
        Order order = Order.builder()
                .orderId(999888777L)
                .symbol("SOLUSDT")
                .side(OrderSide.SELL)
                .orderType("LIMIT")
                .quantity(new BigDecimal("999999.999999999"))
                .averagePrice(new BigDecimal("999999.999999999"))
                .commission(new BigDecimal("999999.999999999"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradePlan tradePlan = TradePlan.builder()
                .leverage(125)
                .build();

        BigDecimal pnl = new BigDecimal("999999.999999999");
        SessionMode sessionMode = SessionMode.SCALPING;
        String context = "Large values test";
        TradingDirection direction = TradingDirection.SHORT;
        OrderPurpose purpose = OrderPurpose.MAIN_OPEN;
        Long parentOrderId = null;
        Long relatedHedgeId = null;

        TradeOrder tradeOrder = new TradeOrder();

        // When
        tradeOrder.onCreate(order, pnl, sessionMode, context, tradePlan, direction, purpose, parentOrderId, relatedHedgeId);

        // Then
        assertEquals(999888777L, tradeOrder.getOrderId());
        assertEquals("Large values test", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.MAIN_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.SHORT, tradeOrder.getDirection());
        assertEquals("SOLUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.SELL, tradeOrder.getSide());
        assertEquals("LIMIT", tradeOrder.getType());
        assertEquals(new BigDecimal("999999.999999999"), tradeOrder.getCount());
        assertEquals(new BigDecimal("999999.999999999"), tradeOrder.getPrice());
        assertEquals(new BigDecimal("999999.999999999"), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(new BigDecimal("999999.999999999"), tradeOrder.getPnl());
        assertEquals(125, tradeOrder.getLeverage());
        assertEquals(SessionMode.SCALPING, tradeOrder.getModeAtCreation());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(0, new BigDecimal("999999999999.998000000000000001").compareTo(tradeOrder.getAmount())); // 999999.999999999 * 999999.999999999
        assertNull(tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
        assertNotNull(tradeOrder.getOrderTime());
    }

    @Test
    @DisplayName("Should handle negative PnL values")
    void testOnCreateWithNegativePnl() {
        // Given
        Order order = Order.builder()
                .orderId(444555666L)
                .symbol("LINKUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("15.50"))
                .commission(new BigDecimal("0.15"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradePlan tradePlan = TradePlan.builder()
                .leverage(15)
                .build();

        BigDecimal pnl = new BigDecimal("-25.75");
        SessionMode sessionMode = SessionMode.HEDGING;
        String context = "Negative PnL test";
        TradingDirection direction = TradingDirection.LONG;
        OrderPurpose purpose = OrderPurpose.HEDGE_OPEN;
        Long parentOrderId = 123456789L;
        Long relatedHedgeId = null;

        TradeOrder tradeOrder = new TradeOrder();

        // When
        tradeOrder.onCreate(order, pnl, sessionMode, context, tradePlan, direction, purpose, parentOrderId, relatedHedgeId);

        // Then
        assertEquals(444555666L, tradeOrder.getOrderId());
        assertEquals("Negative PnL test", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.HEDGE_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("LINKUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(new BigDecimal("10"), tradeOrder.getCount());
        assertEquals(new BigDecimal("15.50"), tradeOrder.getPrice());
        assertEquals(new BigDecimal("0.15"), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(new BigDecimal("-25.75"), tradeOrder.getPnl());
        assertEquals(15, tradeOrder.getLeverage());
        assertEquals(SessionMode.HEDGING, tradeOrder.getModeAtCreation());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(new BigDecimal("155.00"), tradeOrder.getAmount()); // 10 * 15.50
        assertEquals(123456789L, tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
        assertNotNull(tradeOrder.getOrderTime());
    }

    @Test
    @DisplayName("Should handle equals and hashCode based on orderId")
    void testEqualsAndHashCode() {
        // Given
        TradeOrder order1 = TradeOrder.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .build();

        TradeOrder order2 = TradeOrder.builder()
                .orderId(123456789L)
                .symbol("ETHUSDT") // Different symbol
                .build();

        TradeOrder order3 = TradeOrder.builder()
                .orderId(987654321L) // Different orderId
                .symbol("BTCUSDT")
                .build();

        // When & Then
        assertEquals(order1, order2); // Same orderId
        assertNotEquals(order1, order3); // Different orderId
        assertEquals(order1.hashCode(), order2.hashCode());
        assertNotEquals(order1.hashCode(), order3.hashCode());
    }

    @Test
    @DisplayName("Should handle toString method")
    void testToString() {
        // Given
        TradeOrder tradeOrder = TradeOrder.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .build();

        // When
        String toString = tradeOrder.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("123456789"));
        assertTrue(toString.contains("BTCUSDT"));
        assertTrue(toString.contains("BUY"));
    }
} 
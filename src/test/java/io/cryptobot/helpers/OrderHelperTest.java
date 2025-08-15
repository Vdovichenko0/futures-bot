package io.cryptobot.helpers;

import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.enums.OrderType;
import io.cryptobot.binance.order.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderHelper Tests")
class OrderHelperTest {

    @Test
    @DisplayName("Should detect changes in string fields")
    void testStringFieldChanges() {
        // Given
        Order existing = Order.builder()
                .symbol("BTCUSDT")
                .clientOrderId("client1")
                .orderType(OrderType.LIMIT)
                .timeInForce("GTC")
                .executionType("NEW")
                .commissionAsset("BTC")
                .workingType("CONTRACT_PRICE")
                .originalType("LIMIT")
                .positionSide("LONG")
                .originalResponseType("NONE")
                .positionMode("HEDGE_MODE")
                .build();

        Order updated = Order.builder()
                .symbol("ETHUSDT")
                .clientOrderId("client2")
                .orderType(OrderType.MARKET)
                .timeInForce("IOC")
                .executionType("TRADE")
                .commissionAsset("ETH")
                .workingType("MARK_PRICE")
                .originalType("MARKET")
                .positionSide("SHORT")
                .originalResponseType("EXPIRE_TAKER")
                .positionMode("ONE_WAY_MODE")
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("symbol"));
        assertTrue(changes.toString().contains("clientOrderId"));
        assertTrue(changes.toString().contains("orderType"));
        assertTrue(changes.toString().contains("timeInForce"));
        assertTrue(changes.toString().contains("executionType"));
        assertTrue(changes.toString().contains("commissionAsset"));
        assertTrue(changes.toString().contains("workingType"));
        assertTrue(changes.toString().contains("originalType"));
        assertTrue(changes.toString().contains("positionSide"));
        assertTrue(changes.toString().contains("originalResponseType"));
        assertTrue(changes.toString().contains("positionMode"));

        assertEquals("ETHUSDT", existing.getSymbol());
        assertEquals("client2", existing.getClientOrderId());
        assertEquals(OrderType.MARKET, existing.getOrderType());
        assertEquals("IOC", existing.getTimeInForce());
        assertEquals("TRADE", existing.getExecutionType());
        assertEquals("ETH", existing.getCommissionAsset());
        assertEquals("MARK_PRICE", existing.getWorkingType());
        assertEquals("MARKET", existing.getOriginalType());
        assertEquals("SHORT", existing.getPositionSide());
        assertEquals("EXPIRE_TAKER", existing.getOriginalResponseType());
        assertEquals("ONE_WAY_MODE", existing.getPositionMode());
    }

    @Test
    @DisplayName("Should detect changes in enum fields")
    void testEnumFieldChanges() {
        // Given
        Order existing = Order.builder()
                .side(OrderSide.BUY)
                .orderStatus(OrderStatus.NEW)
                .build();

        Order updated = Order.builder()
                .side(OrderSide.SELL)
                .orderStatus(OrderStatus.FILLED)
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("side"));
        assertTrue(changes.toString().contains("orderStatus"));

        assertEquals(OrderSide.SELL, existing.getSide());
        assertEquals(OrderStatus.FILLED, existing.getOrderStatus());
    }

    @Test
    @DisplayName("Should detect changes in BigDecimal fields")
    void testBigDecimalFieldChanges() {
        // Given
        Order existing = Order.builder()
                .quantity(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .averagePrice(new BigDecimal("50000.50"))
                .stopPrice(new BigDecimal("49000.00"))
                .lastFilledQty(new BigDecimal("0.0005"))
                .cumulativeFilledQty(new BigDecimal("0.0005"))
                .lastFilledPrice(new BigDecimal("50000.25"))
                .commission(new BigDecimal("0.0001"))
                .realizedPnl(new BigDecimal("10.50"))
                .build();

        Order updated = Order.builder()
                .quantity(new BigDecimal("0.002"))
                .price(new BigDecimal("51000.00"))
                .averagePrice(new BigDecimal("51000.50"))
                .stopPrice(new BigDecimal("50000.00"))
                .lastFilledQty(new BigDecimal("0.001"))
                .cumulativeFilledQty(new BigDecimal("0.001"))
                .lastFilledPrice(new BigDecimal("51000.25"))
                .commission(new BigDecimal("0.0002"))
                .realizedPnl(new BigDecimal("20.50"))
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("quantity"));
        assertTrue(changes.toString().contains("price"));
        assertTrue(changes.toString().contains("averagePrice"));
        assertTrue(changes.toString().contains("stopPrice"));
        assertTrue(changes.toString().contains("lastFilledQty"));
        assertTrue(changes.toString().contains("cumulativeFilledQty"));
        assertTrue(changes.toString().contains("lastFilledPrice"));
        assertTrue(changes.toString().contains("commission"));
        assertTrue(changes.toString().contains("realizedPnl"));

        assertEquals(new BigDecimal("0.002"), existing.getQuantity());
        assertEquals(new BigDecimal("51000.00"), existing.getPrice());
        assertEquals(new BigDecimal("51000.50"), existing.getAveragePrice());
        assertEquals(new BigDecimal("50000.00"), existing.getStopPrice());
        assertEquals(new BigDecimal("0.001"), existing.getLastFilledQty());
        assertEquals(new BigDecimal("0.001"), existing.getCumulativeFilledQty());
        assertEquals(new BigDecimal("51000.25"), existing.getLastFilledPrice());
        assertEquals(new BigDecimal("0.0002"), existing.getCommission());
        assertEquals(new BigDecimal("20.50"), existing.getRealizedPnl());
    }

    @Test
    @DisplayName("Should detect changes in primitive fields")
    void testPrimitiveFieldChanges() {
        // Given
        Order existing = Order.builder()
                .tradeTime(1640995200000L)
                .tradeId(12345L)
                .buyerIsMaker(false)
                .reduceOnly(false)
                .closePosition(false)
                .isPositionPnl(false)
                .sideEffectType(0)
                .stopStatus(0)
                .goodTillDate(1640998800000L)
                .build();

        Order updated = Order.builder()
                .tradeTime(1640995300000L)
                .tradeId(12346L)
                .buyerIsMaker(true)
                .reduceOnly(true)
                .closePosition(true)
                .isPositionPnl(true)
                .sideEffectType(1)
                .stopStatus(1)
                .goodTillDate(1640998900000L)
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("tradeTime"));
        assertTrue(changes.toString().contains("tradeId"));
        assertTrue(changes.toString().contains("buyerIsMaker"));
        assertTrue(changes.toString().contains("reduceOnly"));
        assertTrue(changes.toString().contains("closePosition"));
        assertTrue(changes.toString().contains("isPositionPnl"));
        assertTrue(changes.toString().contains("sideEffectType"));
        assertTrue(changes.toString().contains("stopStatus"));
        assertTrue(changes.toString().contains("goodTillDate"));

        assertEquals(1640995300000L, existing.getTradeTime());
        assertEquals(12346L, existing.getTradeId());
        assertTrue(existing.isBuyerIsMaker());
        assertTrue(existing.isReduceOnly());
        assertTrue(existing.isClosePosition());
        assertTrue(existing.isPositionPnl());
        assertEquals(1, existing.getSideEffectType());
        assertEquals(1, existing.getStopStatus());
        assertEquals(1640998900000L, existing.getGoodTillDate());
    }

    @Test
    @DisplayName("Should not detect changes when values are the same")
    void testNoChanges() {
        // Given
        Order existing = Order.builder()
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderStatus(OrderStatus.FILLED)
                .quantity(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .tradeTime(1640995200000L)
                .tradeId(12345L)
                .buyerIsMaker(false)
                .reduceOnly(false)
                .build();

        Order updated = Order.builder()
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderStatus(OrderStatus.FILLED)
                .quantity(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .tradeTime(1640995200000L)
                .tradeId(12345L)
                .buyerIsMaker(false)
                .reduceOnly(false)
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertFalse(changed);
        assertEquals("", changes.toString());
    }

    @Test
    @DisplayName("Should handle null values correctly")
    void testNullValues() {
        // Given
        Order existing = Order.builder()
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .quantity(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .build();

        Order updated = Order.builder()
                .symbol(null)
                .side(null)
                .quantity(null)
                .price(null)
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("symbol"));
        assertTrue(changes.toString().contains("side"));
        assertTrue(changes.toString().contains("quantity"));
        assertTrue(changes.toString().contains("price"));

        assertNull(existing.getSymbol());
        assertNull(existing.getSide());
        assertNull(existing.getQuantity());
        assertNull(existing.getPrice());
    }

    @Test
    @DisplayName("Should handle BigDecimal normalization")
    void testBigDecimalNormalization() {
        // Given
        Order existing = Order.builder()
                .quantity(new BigDecimal("0.001000"))
                .price(new BigDecimal("50000.000"))
                .build();

        Order updated = Order.builder()
                .quantity(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertFalse(changed); // Should not detect changes due to normalization
        assertEquals("", changes.toString());
    }

    @Test
    @DisplayName("Should handle mixed changes")
    void testMixedChanges() {
        // Given
        Order existing = Order.builder()
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .quantity(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .tradeTime(1640995200000L)
                .build();

        Order updated = Order.builder()
                .symbol("ETHUSDT")
                .side(OrderSide.SELL)
                .quantity(new BigDecimal("0.002"))
                .price(new BigDecimal("3000.00"))
                .tradeTime(1640995300000L)
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("symbol"));
        assertTrue(changes.toString().contains("side"));
        assertTrue(changes.toString().contains("quantity"));
        assertTrue(changes.toString().contains("price"));
        assertTrue(changes.toString().contains("tradeTime"));

        assertEquals("ETHUSDT", existing.getSymbol());
        assertEquals(OrderSide.SELL, existing.getSide());
        assertEquals(new BigDecimal("0.002"), existing.getQuantity());
        assertEquals(new BigDecimal("3000.00"), existing.getPrice());
        assertEquals(1640995300000L, existing.getTradeTime());
    }

    @Test
    @DisplayName("Should handle empty string values")
    void testEmptyStringValues() {
        // Given
        Order existing = Order.builder()
                .symbol("BTCUSDT")
                .clientOrderId("client1")
                .build();

        Order updated = Order.builder()
                .symbol("")
                .clientOrderId("")
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("symbol"));
        assertTrue(changes.toString().contains("clientOrderId"));

        assertEquals("", existing.getSymbol());
        assertEquals("", existing.getClientOrderId());
    }

    @Test
    @DisplayName("Should handle zero BigDecimal values")
    void testZeroBigDecimalValues() {
        // Given
        Order existing = Order.builder()
                .quantity(new BigDecimal("0.001"))
                .price(new BigDecimal("50000.00"))
                .build();

        Order updated = Order.builder()
                .quantity(BigDecimal.ZERO)
                .price(BigDecimal.ZERO)
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("quantity"));
        assertTrue(changes.toString().contains("price"));

        assertEquals(BigDecimal.ZERO, existing.getQuantity());
        assertEquals(BigDecimal.ZERO, existing.getPrice());
    }

    @Test
    @DisplayName("Should handle large BigDecimal values")
    void testLargeBigDecimalValues() {
        // Given
        Order existing = Order.builder()
                .quantity(new BigDecimal("1000000.123456"))
                .price(new BigDecimal("999999.999999"))
                .build();

        Order updated = Order.builder()
                .quantity(new BigDecimal("2000000.654321"))
                .price(new BigDecimal("888888.111111"))
                .build();

        StringBuilder changes = new StringBuilder();

        // When
        boolean changed = OrderHelper.mergeAndDetectChanges(existing, updated, changes);

        // Then
        assertTrue(changed);
        assertTrue(changes.toString().contains("quantity"));
        assertTrue(changes.toString().contains("price"));

        assertEquals(new BigDecimal("2000000.654321"), existing.getQuantity());
        assertEquals(new BigDecimal("888888.111111"), existing.getPrice());
    }
} 
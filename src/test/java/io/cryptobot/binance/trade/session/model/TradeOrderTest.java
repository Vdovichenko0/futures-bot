package io.cryptobot.binance.trade.session.model;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TradeOrderTest {

    private TradeOrder tradeOrder;
    private Order binanceOrder;
    private TradePlan tradePlan;

    @BeforeEach
    void setUp() {
        tradePlan = TradePlan.builder()
                .symbol("BTCUSDT")
                .leverage(10)
                .build();
        
        binanceOrder = Order.builder()
                .orderId(1001L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(50000))
                .commission(BigDecimal.valueOf(0.5))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();
    }

    @Test
    void testTradeOrderCreation() {
        // Given
        tradeOrder = TradeOrder.builder()
                .orderId(1001L)
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .type("MARKET")
                .count(BigDecimal.valueOf(0.1))
                .price(BigDecimal.valueOf(50000))
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        // Then
        assertEquals(1001L, tradeOrder.getOrderId());
        assertEquals(OrderPurpose.MAIN_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("BTCUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(BigDecimal.valueOf(0.1), tradeOrder.getCount());
        assertEquals(BigDecimal.valueOf(50000), tradeOrder.getPrice());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertNotNull(tradeOrder.getOrderTime());
    }

    @Test
    void testOnCreateMethod() {
        // Given
        tradeOrder = new TradeOrder();
        BigDecimal pnl = BigDecimal.valueOf(10.5);

        // When
        tradeOrder.onCreate(binanceOrder, pnl, SessionMode.SCALPING, "test_context", 
                tradePlan, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, null, null);

        // Then
        assertEquals(1001L, tradeOrder.getOrderId());
        assertEquals("test_context", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.MAIN_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("BTCUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(BigDecimal.valueOf(0.1), tradeOrder.getCount());
        assertEquals(BigDecimal.valueOf(50000), tradeOrder.getPrice());
        assertEquals(BigDecimal.valueOf(0.5), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(pnl, tradeOrder.getPnl());
        assertEquals(10, tradeOrder.getLeverage());
        assertEquals(SessionMode.SCALPING, tradeOrder.getModeAtCreation());
        assertEquals(BigDecimal.valueOf(5000.0), tradeOrder.getAmount()); // 0.1 * 50000
        assertNull(tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertFalse(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertNull(tradeOrder.getBasePnl());
        assertNull(tradeOrder.getMaxChangePnl());
    }

    @Test
    void testOnCreateWithParentOrder() {
        // Given
        tradeOrder = new TradeOrder();
        Long parentOrderId = 1000L;
        Long relatedHedgeId = 1002L;

        // When
        tradeOrder.onCreate(binanceOrder, BigDecimal.valueOf(5.0), SessionMode.HEDGING, "hedge_context", 
                tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, parentOrderId, relatedHedgeId);

        // Then
        assertEquals(parentOrderId, tradeOrder.getParentOrderId());
        assertEquals(relatedHedgeId, tradeOrder.getRelatedHedgeId());
        assertEquals(OrderPurpose.HEDGE_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.SHORT, tradeOrder.getDirection());
        assertEquals(SessionMode.HEDGING, tradeOrder.getModeAtCreation());
    }

    @Test
    void testBuilderPattern() {
        // Given & When
        tradeOrder = TradeOrder.builder()
                .orderId(1001L)
                .creationContext("test")
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .type("MARKET")
                .count(BigDecimal.valueOf(0.1))
                .price(BigDecimal.valueOf(50000))
                .amount(BigDecimal.valueOf(5000))
                .commission(BigDecimal.valueOf(0.5))
                .commissionAsset("USDT")
                .status(OrderStatus.FILLED)
                .pnl(BigDecimal.valueOf(10.5))
                .leverage(10)
                .parentOrderId(null)
                .relatedHedgeId(null)
                .modeAtCreation(SessionMode.SCALPING)
                .orderTime(LocalDateTime.now())
                .pnlHigh(BigDecimal.valueOf(15.0))
                .trailingActive(true)
                .basePnl(BigDecimal.valueOf(-5.0))
                .maxChangePnl(BigDecimal.valueOf(20.0))
                .build();

        // Then
        assertEquals(1001L, tradeOrder.getOrderId());
        assertEquals("test", tradeOrder.getCreationContext());
        assertEquals(OrderPurpose.MAIN_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals("BTCUSDT", tradeOrder.getSymbol());
        assertEquals(OrderSide.BUY, tradeOrder.getSide());
        assertEquals("MARKET", tradeOrder.getType());
        assertEquals(BigDecimal.valueOf(0.1), tradeOrder.getCount());
        assertEquals(BigDecimal.valueOf(50000), tradeOrder.getPrice());
        assertEquals(BigDecimal.valueOf(5000), tradeOrder.getAmount());
        assertEquals(BigDecimal.valueOf(0.5), tradeOrder.getCommission());
        assertEquals("USDT", tradeOrder.getCommissionAsset());
        assertEquals(OrderStatus.FILLED, tradeOrder.getStatus());
        assertEquals(BigDecimal.valueOf(10.5), tradeOrder.getPnl());
        assertEquals(10, tradeOrder.getLeverage());
        assertNull(tradeOrder.getParentOrderId());
        assertNull(tradeOrder.getRelatedHedgeId());
        assertEquals(SessionMode.SCALPING, tradeOrder.getModeAtCreation());
        assertNotNull(tradeOrder.getOrderTime());
        assertEquals(BigDecimal.valueOf(15.0), tradeOrder.getPnlHigh());
        assertTrue(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.valueOf(-5.0), tradeOrder.getBasePnl());
        assertEquals(BigDecimal.valueOf(20.0), tradeOrder.getMaxChangePnl());
    }

    @Test
    void testToBuilder() {
        // Given
        tradeOrder = TradeOrder.builder()
                .orderId(1001L)
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .status(OrderStatus.FILLED)
                .build();

        // When
        TradeOrder updatedOrder = tradeOrder.toBuilder()
                .pnl(BigDecimal.valueOf(15.0))
                .trailingActive(true)
                .pnlHigh(BigDecimal.valueOf(20.0))
                .basePnl(BigDecimal.valueOf(-5.0))
                .maxChangePnl(BigDecimal.valueOf(25.0))
                .build();

        // Then
        assertEquals(1001L, updatedOrder.getOrderId());
        assertEquals(OrderPurpose.MAIN_OPEN, updatedOrder.getPurpose());
        assertEquals(TradingDirection.LONG, updatedOrder.getDirection());
        assertEquals("BTCUSDT", updatedOrder.getSymbol());
        assertEquals(OrderStatus.FILLED, updatedOrder.getStatus());
        assertEquals(BigDecimal.valueOf(15.0), updatedOrder.getPnl());
        assertTrue(updatedOrder.getTrailingActive());
        assertEquals(BigDecimal.valueOf(20.0), updatedOrder.getPnlHigh());
        assertEquals(BigDecimal.valueOf(-5.0), updatedOrder.getBasePnl());
        assertEquals(BigDecimal.valueOf(25.0), updatedOrder.getMaxChangePnl());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        TradeOrder order1 = TradeOrder.builder()
                .orderId(1001L)
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .status(OrderStatus.FILLED)
                .build();

        TradeOrder order2 = TradeOrder.builder()
                .orderId(1001L)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .direction(TradingDirection.SHORT)
                .symbol("ETHUSDT")
                .status(OrderStatus.NEW)
                .build();

        TradeOrder order3 = TradeOrder.builder()
                .orderId(1002L)
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .status(OrderStatus.FILLED)
                .build();

        // Then
        assertEquals(order1, order2); // равны по orderId
        assertNotEquals(order1, order3); // разные orderId
        assertEquals(order1.hashCode(), order2.hashCode());
        assertNotEquals(order1.hashCode(), order3.hashCode());
    }

    @Test
    void testToString() {
        // Given
        tradeOrder = TradeOrder.builder()
                .orderId(1001L)
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .status(OrderStatus.FILLED)
                .build();

        // When
        String result = tradeOrder.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("1001"));
        assertTrue(result.contains("MAIN_OPEN"));
        assertTrue(result.contains("LONG"));
        assertTrue(result.contains("BTCUSDT"));
        assertTrue(result.contains("FILLED"));
    }

    @Test
    void testDefaultValues() {
        // Given
        tradeOrder = new TradeOrder();

        // Then
        assertEquals(BigDecimal.ZERO, tradeOrder.getAmount());
        assertEquals(BigDecimal.ZERO, tradeOrder.getCommission());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnl());
        assertEquals(BigDecimal.ZERO, tradeOrder.getPnlHigh());
        assertFalse(tradeOrder.getTrailingActive());
    }

    @Test
    void testSetters() {
        // Given
        tradeOrder = new TradeOrder();

        // When
        tradeOrder.setPnlHigh(BigDecimal.valueOf(15.0));
        tradeOrder.setTrailingActive(true);
        tradeOrder.setBasePnl(BigDecimal.valueOf(-5.0));
        tradeOrder.setMaxChangePnl(BigDecimal.valueOf(20.0));

        // Then
        assertEquals(BigDecimal.valueOf(15.0), tradeOrder.getPnlHigh());
        assertTrue(tradeOrder.getTrailingActive());
        assertEquals(BigDecimal.valueOf(-5.0), tradeOrder.getBasePnl());
        assertEquals(BigDecimal.valueOf(20.0), tradeOrder.getMaxChangePnl());
    }

    @Test
    void testHedgeOrderCreation() {
        // Given
        tradeOrder = new TradeOrder();
        Long parentOrderId = 1000L;

        // When
        tradeOrder.onCreate(binanceOrder, BigDecimal.valueOf(-5.0), SessionMode.HEDGING, "hedge_worsening", 
                tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, parentOrderId, null);

        // Then
        assertEquals(OrderPurpose.HEDGE_OPEN, tradeOrder.getPurpose());
        assertEquals(TradingDirection.SHORT, tradeOrder.getDirection());
        assertEquals(parentOrderId, tradeOrder.getParentOrderId());
        assertEquals(SessionMode.HEDGING, tradeOrder.getModeAtCreation());
        assertEquals("hedge_worsening", tradeOrder.getCreationContext());
    }

    @Test
    void testCloseOrderCreation() {
        // Given
        tradeOrder = new TradeOrder();
        Long relatedHedgeId = 1002L;

        // When
        tradeOrder.onCreate(binanceOrder, BigDecimal.valueOf(10.0), SessionMode.HEDGING, "close_trailing", 
                tradePlan, TradingDirection.LONG, OrderPurpose.HEDGE_CLOSE, null, relatedHedgeId);

        // Then
        assertEquals(OrderPurpose.HEDGE_CLOSE, tradeOrder.getPurpose());
        assertEquals(TradingDirection.LONG, tradeOrder.getDirection());
        assertEquals(relatedHedgeId, tradeOrder.getRelatedHedgeId());
        assertEquals(SessionMode.HEDGING, tradeOrder.getModeAtCreation());
        assertEquals("close_trailing", tradeOrder.getCreationContext());
    }

    @Test
    @DisplayName("Should create averaging order with correct weighted average price")
    void shouldCreateAveragingOrderWithCorrectWeightedAveragePrice() {
        // Given
        Order averagingOrder = Order.builder()
                .orderId(2001L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.5"))
                .averagePrice(new BigDecimal("48000")) // Усреднение по 48000
                .commission(new BigDecimal("2.0"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder parentOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .count(new BigDecimal("1.0"))
                .price(new BigDecimal("50000")) // Основной ордер по 50000
                .build();

        TradePlan plan = TradePlan.builder()
                .leverage(10)
                .build();

        TradeOrder averagingTradeOrder = new TradeOrder();

        // When
        averagingTradeOrder.onCreateAverage(
                averagingOrder,
                parentOrder,
                BigDecimal.ZERO,
                SessionMode.SCALPING,
                "test averaging",
                plan,
                TradingDirection.LONG,
                OrderPurpose.AVERAGING_OPEN
        );

        // Then
        assertEquals(2001L, averagingTradeOrder.getOrderId());
        assertEquals(TradingDirection.LONG, averagingTradeOrder.getDirection());
        assertEquals(OrderPurpose.AVERAGING_OPEN, averagingTradeOrder.getPurpose());
        assertEquals(1001L, averagingTradeOrder.getParentOrderId());
        assertNull(averagingTradeOrder.getRelatedHedgeId());

        // Проверяем средневзвешенную цену: (1.0 * 50000 + 0.5 * 48000) / (1.0 + 0.5) = 49333.33...
        BigDecimal expectedPrice = new BigDecimal("49333.33333333");
        assertEquals(expectedPrice, averagingTradeOrder.getPrice());
        
        // Проверяем общее количество (базовый + новый)
        assertEquals(new BigDecimal("1.5"), averagingTradeOrder.getCount());

        // Проверяем что родительский ордер помечен как имеющий усреднение
        assertTrue(parentOrder.getHaveAveraging());
        assertEquals(2001L, parentOrder.getIdAveragingOrder());
    }

    @Test
    @DisplayName("Should create averaging order with correct weighted average price for SHORT")
    void shouldCreateAveragingOrderWithCorrectWeightedAveragePriceForShort() {
        // Given
        Order averagingOrder = Order.builder()
                .orderId(2002L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.8"))
                .averagePrice(new BigDecimal("52000")) // Усреднение по 52000
                .commission(new BigDecimal("3.0"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder parentOrder = TradeOrder.builder()
                .orderId(1002L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.MAIN_OPEN)
                .count(new BigDecimal("2.0"))
                .price(new BigDecimal("50000")) // Основной ордер по 50000
                .build();

        TradePlan plan = TradePlan.builder()
                .leverage(10)
                .build();

        TradeOrder averagingTradeOrder = new TradeOrder();

        // When
        averagingTradeOrder.onCreateAverage(
                averagingOrder,
                parentOrder,
                BigDecimal.ZERO,
                SessionMode.SCALPING,
                "test averaging short",
                plan,
                TradingDirection.SHORT,
                OrderPurpose.AVERAGING_OPEN
        );

        // Then
        assertEquals(2002L, averagingTradeOrder.getOrderId());
        assertEquals(TradingDirection.SHORT, averagingTradeOrder.getDirection());
        assertEquals(OrderPurpose.AVERAGING_OPEN, averagingTradeOrder.getPurpose());
        assertEquals(1002L, averagingTradeOrder.getParentOrderId());

        // Проверяем средневзвешенную цену: (2.0 * 50000 + 0.8 * 52000) / (2.0 + 0.8) = 50571.43...
        BigDecimal expectedPrice = new BigDecimal("50571.42857143");
        assertEquals(expectedPrice, averagingTradeOrder.getPrice());
        
        // Проверяем общее количество (базовый + новый)
        assertEquals(new BigDecimal("2.8"), averagingTradeOrder.getCount());

        // Проверяем что родительский ордер помечен как имеющий усреднение
        assertTrue(parentOrder.getHaveAveraging());
        assertEquals(2002L, parentOrder.getIdAveragingOrder());
    }

    @Test
    @DisplayName("Should handle averaging order with null parent order")
    void shouldHandleAveragingOrderWithNullParentOrder() {
        // Given
        Order averagingOrder = Order.builder()
                .orderId(2003L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.5"))
                .averagePrice(new BigDecimal("48000"))
                .commission(new BigDecimal("2.0"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradePlan plan = TradePlan.builder()
                .leverage(10)
                .build();

        TradeOrder averagingTradeOrder = new TradeOrder();

        // When
        averagingTradeOrder.onCreateAverage(
                averagingOrder,
                null, // null parent order
                BigDecimal.ZERO,
                SessionMode.SCALPING,
                "test averaging no parent",
                plan,
                TradingDirection.LONG,
                OrderPurpose.AVERAGING_OPEN
        );

        // Then
        assertEquals(2003L, averagingTradeOrder.getOrderId());
        assertEquals(TradingDirection.LONG, averagingTradeOrder.getDirection());
        assertEquals(OrderPurpose.AVERAGING_OPEN, averagingTradeOrder.getPurpose());
        assertEquals(new BigDecimal("0.5"), averagingTradeOrder.getCount());
        assertNull(averagingTradeOrder.getParentOrderId());

        // При null parent order цена должна быть равна цене усреднения
        assertEquals(new BigDecimal("48000.00000000"), averagingTradeOrder.getPrice());
    }

    @Test
    @DisplayName("Should handle averaging order with zero quantities")
    void shouldHandleAveragingOrderWithZeroQuantities() {
        // Given
        Order averagingOrder = Order.builder()
                .orderId(2004L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(BigDecimal.ZERO) // Нулевое количество
                .averagePrice(new BigDecimal("48000"))
                .commission(new BigDecimal("2.0"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder parentOrder = TradeOrder.builder()
                .orderId(1004L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .count(BigDecimal.ZERO) // Нулевое количество
                .price(new BigDecimal("50000"))
                .build();

        TradePlan plan = TradePlan.builder()
                .leverage(10)
                .build();

        TradeOrder averagingTradeOrder = new TradeOrder();

        // When
        averagingTradeOrder.onCreateAverage(
                averagingOrder,
                parentOrder,
                BigDecimal.ZERO,
                SessionMode.SCALPING,
                "test averaging zero quantities",
                plan,
                TradingDirection.LONG,
                OrderPurpose.AVERAGING_OPEN
        );

        // Then
        assertEquals(2004L, averagingTradeOrder.getOrderId());
        assertEquals(TradingDirection.LONG, averagingTradeOrder.getDirection());
        assertEquals(OrderPurpose.AVERAGING_OPEN, averagingTradeOrder.getPurpose());
        assertEquals(BigDecimal.ZERO, averagingTradeOrder.getCount());
        assertEquals(1004L, averagingTradeOrder.getParentOrderId());

        // При нулевых количествах цена должна быть равна цене усреднения (fallback)
        assertEquals(new BigDecimal("48000"), averagingTradeOrder.getPrice());
    }
} 
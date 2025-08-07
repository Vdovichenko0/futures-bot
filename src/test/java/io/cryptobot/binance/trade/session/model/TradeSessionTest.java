package io.cryptobot.binance.trade.session.model;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradeSession Tests")
class TradeSessionTest {

    private TradeSession tradeSession;
    private TradeOrder mainOrder;
    private TradePlan tradePlan;

    @BeforeEach
    void setUp() {
        tradeSession = new TradeSession();
        
        // Создаем основной ордер
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

        tradePlan = TradePlan.builder()
                .leverage(10)
                .build();

        mainOrder = new TradeOrder();
        mainOrder.onCreate(order, new BigDecimal("10.50"), SessionMode.SCALPING, 
                "Test main order", tradePlan, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                null, null);
    }

    @Test
    @DisplayName("Should create session with LONG direction")
    void testCreateSessionWithLongDirection() {
        // Given
        String plan = "BTCUSDT_plan";
        TradingDirection direction = TradingDirection.LONG;
        String context = "Test session creation";

        // When
        tradeSession.onCreate(plan, direction, mainOrder, context);

        // Then
        assertEquals(plan, tradeSession.getTradePlan());
        assertEquals(SessionStatus.ACTIVE, tradeSession.getStatus());
        assertEquals(SessionMode.SCALPING, tradeSession.getCurrentMode());
        assertEquals(TradingDirection.LONG, tradeSession.getDirection());
        assertEquals(context, tradeSession.getEntryContext());
        assertEquals(123456789L, tradeSession.getMainPosition());
        assertEquals(1, tradeSession.getOrders().size());
        assertEquals(mainOrder, tradeSession.getOrders().get(0));
        assertTrue(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
        assertTrue(tradeSession.hasActivePosition());
        assertFalse(tradeSession.hasBothPositionsActive());
        assertNotNull(tradeSession.getCreatedTime());
        assertNull(tradeSession.getEndTime());
        assertEquals(new BigDecimal("10.50"), tradeSession.getPnl());
        assertEquals(new BigDecimal("0.05"), tradeSession.getTotalCommission());
        assertEquals(0, tradeSession.getHedgeOpenCount());
        assertEquals(0, tradeSession.getHedgeCloseCount());
    }

    @Test
    @DisplayName("Should create session with SHORT direction")
    void testCreateSessionWithShortDirection() {
        // Given
        Order shortOrder = Order.builder()
                .orderId(987654321L)
                .symbol("ETHUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.01"))
                .averagePrice(new BigDecimal("3000.00"))
                .commission(new BigDecimal("0.03"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder mainShortOrder = new TradeOrder();
        mainShortOrder.onCreate(shortOrder, new BigDecimal("-5.25"), SessionMode.SCALPING,
                "Test short order", tradePlan, TradingDirection.SHORT, OrderPurpose.MAIN_OPEN,
                null, null);

        String plan = "ETHUSDT_plan";
        TradingDirection direction = TradingDirection.SHORT;
        String context = "Test short session creation";

        // When
        tradeSession.onCreate(plan, direction, mainShortOrder, context);

        // Then
        assertEquals(plan, tradeSession.getTradePlan());
        assertEquals(SessionStatus.ACTIVE, tradeSession.getStatus());
        assertEquals(SessionMode.SCALPING, tradeSession.getCurrentMode());
        assertEquals(TradingDirection.SHORT, tradeSession.getDirection());
        assertEquals(context, tradeSession.getEntryContext());
        assertEquals(987654321L, tradeSession.getMainPosition());
        assertEquals(1, tradeSession.getOrders().size());
        assertEquals(mainShortOrder, tradeSession.getOrders().get(0));
        assertFalse(tradeSession.isActiveLong());
        assertTrue(tradeSession.isActiveShort());
        assertTrue(tradeSession.hasActivePosition());
        assertFalse(tradeSession.hasBothPositionsActive());
        assertNotNull(tradeSession.getCreatedTime());
        assertNull(tradeSession.getEndTime());
        assertEquals(new BigDecimal("-5.25"), tradeSession.getPnl());
        assertEquals(new BigDecimal("0.03"), tradeSession.getTotalCommission());
        assertEquals(0, tradeSession.getHedgeOpenCount());
        assertEquals(0, tradeSession.getHedgeCloseCount());
    }

    @Test
    @DisplayName("Should add hedge order and switch to HEDGING mode")
    void testAddHedgeOrder() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        Order hedgeOrder = Order.builder()
                .orderId(555666777L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("49000.00"))
                .commission(new BigDecimal("0.04"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder hedgeTradeOrder = new TradeOrder();
        hedgeTradeOrder.onCreate(hedgeOrder, new BigDecimal("-15.75"), SessionMode.HEDGING,
                "Hedge order", tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN,
                123456789L, null);

        // When
        tradeSession.addOrder(hedgeTradeOrder);

        // Then
        assertEquals(2, tradeSession.getOrders().size());
        assertEquals(SessionMode.HEDGING, tradeSession.getCurrentMode());
        assertTrue(tradeSession.isActiveLong());
        assertTrue(tradeSession.isActiveShort());
        assertTrue(tradeSession.hasBothPositionsActive());
        assertTrue(tradeSession.hasActivePosition());
        assertEquals(new BigDecimal("-5.25"), tradeSession.getPnl()); // 10.50 + (-15.75)
        assertEquals(new BigDecimal("0.09"), tradeSession.getTotalCommission()); // 0.05 + 0.04
        assertEquals(1, tradeSession.getHedgeOpenCount());
        assertEquals(0, tradeSession.getHedgeCloseCount());
    }

    @Test
    @DisplayName("Should close hedge and return to SCALPING mode")
    void testCloseHedgeAndReturnToScalping() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Добавляем хедж
        Order hedgeOrder = Order.builder()
                .orderId(555666777L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("49000.00"))
                .commission(new BigDecimal("0.04"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder hedgeTradeOrder = new TradeOrder();
        hedgeTradeOrder.onCreate(hedgeOrder, new BigDecimal("-15.75"), SessionMode.HEDGING,
                "Hedge order", tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN,
                123456789L, null);

        tradeSession.addOrder(hedgeTradeOrder);

        // Закрываем хедж
        Order closeHedgeOrder = Order.builder()
                .orderId(888999000L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("49500.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder closeHedgeTradeOrder = new TradeOrder();
        closeHedgeTradeOrder.onCreate(closeHedgeOrder, new BigDecimal("5.00"), SessionMode.HEDGING,
                "Close hedge order", tradePlan, TradingDirection.LONG, OrderPurpose.HEDGE_CLOSE,
                123456789L, 555666777L);

        // When
        tradeSession.addOrder(closeHedgeTradeOrder);

        // Then
        assertEquals(3, tradeSession.getOrders().size());
        assertEquals(SessionMode.SCALPING, tradeSession.getCurrentMode());
        assertTrue(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
        assertFalse(tradeSession.hasBothPositionsActive());
        assertTrue(tradeSession.hasActivePosition());
        assertEquals(new BigDecimal("-0.25"), tradeSession.getPnl()); // 10.50 + (-15.75) + 5.00
        assertEquals(new BigDecimal("0.14"), tradeSession.getTotalCommission()); // 0.05 + 0.04 + 0.05
        assertEquals(1, tradeSession.getHedgeOpenCount());
        assertEquals(1, tradeSession.getHedgeCloseCount());
    }

    @Test
    @DisplayName("Should complete session when no active positions")
    void testCompleteSessionWhenNoActivePositions() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Закрываем основную позицию
        Order closeMainOrder = Order.builder()
                .orderId(111222333L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("51000.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder closeMainTradeOrder = new TradeOrder();
        closeMainTradeOrder.onCreate(closeMainOrder, new BigDecimal("10.00"), SessionMode.SCALPING,
                "Close main order", tradePlan, TradingDirection.SHORT, OrderPurpose.MAIN_CLOSE,
                null, null);

        // When
        tradeSession.addOrder(closeMainTradeOrder);

        // Then
        assertEquals(SessionStatus.COMPLETED, tradeSession.getStatus());
        assertNotNull(tradeSession.getEndTime());
        assertNotNull(tradeSession.getDurationMinutes());
        assertFalse(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
        assertFalse(tradeSession.hasActivePosition());
        assertEquals(new BigDecimal("20.50"), tradeSession.getPnl()); // 10.50 + 10.00
        assertEquals(new BigDecimal("0.10"), tradeSession.getTotalCommission()); // 0.05 + 0.05
    }

    @Test
    @DisplayName("Should handle partial hedge close")
    void testPartialHedgeClose() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Добавляем хедж
        Order hedgeOrder = Order.builder()
                .orderId(555666777L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("49000.00"))
                .commission(new BigDecimal("0.04"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder hedgeTradeOrder = new TradeOrder();
        hedgeTradeOrder.onCreate(hedgeOrder, new BigDecimal("-15.75"), SessionMode.HEDGING,
                "Hedge order", tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN,
                123456789L, null);

        tradeSession.addOrder(hedgeTradeOrder);

        // Частично закрываем хедж
        Order partialCloseOrder = Order.builder()
                .orderId(444555666L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.0005"))
                .averagePrice(new BigDecimal("49500.00"))
                .commission(new BigDecimal("0.025"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder partialCloseTradeOrder = new TradeOrder();
        partialCloseTradeOrder.onCreate(partialCloseOrder, new BigDecimal("2.50"), SessionMode.HEDGING,
                "Partial close hedge", tradePlan, TradingDirection.LONG, OrderPurpose.HEDGE_PARTIAL_CLOSE,
                123456789L, 555666777L);

        // When
        tradeSession.addOrder(partialCloseTradeOrder);

        // Then
        assertEquals(3, tradeSession.getOrders().size());
        assertEquals(SessionMode.SCALPING, tradeSession.getCurrentMode()); // Переходит в SCALPING режим
        assertTrue(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort()); // Частичное закрытие закрывает хедж
        assertFalse(tradeSession.hasBothPositionsActive());
        assertEquals(new BigDecimal("-2.75"), tradeSession.getPnl()); // 10.50 + (-15.75) + 2.50
        assertEquals(new BigDecimal("0.115"), tradeSession.getTotalCommission()); // 0.05 + 0.04 + 0.025
        assertEquals(1, tradeSession.getHedgeOpenCount());
        assertEquals(0, tradeSession.getHedgeCloseCount());
    }

    @Test
    @DisplayName("Should handle force close")
    void testForceClose() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Форсированное закрытие
        Order forceCloseOrder = Order.builder()
                .orderId(777888999L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("48000.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder forceCloseTradeOrder = new TradeOrder();
        forceCloseTradeOrder.onCreate(forceCloseOrder, new BigDecimal("-20.00"), SessionMode.SCALPING,
                "Force close", tradePlan, TradingDirection.SHORT, OrderPurpose.FORCE_CLOSE,
                null, null);

        // When
        tradeSession.addOrder(forceCloseTradeOrder);

        // Then
        assertEquals(SessionStatus.COMPLETED, tradeSession.getStatus());
        assertFalse(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
        assertFalse(tradeSession.hasActivePosition());
        assertEquals(new BigDecimal("-9.50"), tradeSession.getPnl()); // 10.50 + (-20.00)
        assertEquals(new BigDecimal("0.10"), tradeSession.getTotalCommission()); // 0.05 + 0.05
    }

    @Test
    @DisplayName("Should handle cancelled order")
    void testCancelledOrder() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Отмененный ордер
        Order cancelOrder = Order.builder()
                .orderId(999888777L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("LIMIT")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(BigDecimal.ZERO)
                .commission(BigDecimal.ZERO)
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.CANCELLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder cancelTradeOrder = new TradeOrder();
        cancelTradeOrder.onCreate(cancelOrder, BigDecimal.ZERO, SessionMode.SCALPING,
                "Cancelled order", tradePlan, TradingDirection.SHORT, OrderPurpose.CANCEL,
                null, null);

        // When
        tradeSession.addOrder(cancelTradeOrder);

        // Then
        assertEquals(2, tradeSession.getOrders().size());
        assertEquals(SessionMode.SCALPING, tradeSession.getCurrentMode()); // Режим не меняется
        assertTrue(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
        assertTrue(tradeSession.hasActivePosition());
        assertEquals(new BigDecimal("10.50"), tradeSession.getPnl()); // PnL не меняется
        assertEquals(new BigDecimal("0.05"), tradeSession.getTotalCommission()); // Комиссия не меняется
        assertEquals(0, tradeSession.getHedgeOpenCount());
        assertEquals(0, tradeSession.getHedgeCloseCount());
    }

    @Test
    @DisplayName("Should find order by ID")
    void testFindOrderById() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // When
        TradeOrder foundOrder = tradeSession.findOrderById(123456789L);
        TradeOrder notFoundOrder = tradeSession.findOrderById(999999999L);

        // Then
        assertNotNull(foundOrder);
        assertEquals(123456789L, foundOrder.getOrderId());
        assertNull(notFoundOrder);
    }

    @Test
    @DisplayName("Should get main order")
    void testGetMainOrder() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // When
        TradeOrder mainOrderFound = tradeSession.getMainOrder();

        // Then
        assertNotNull(mainOrderFound);
        assertEquals(123456789L, mainOrderFound.getOrderId());
        assertEquals(OrderPurpose.MAIN_OPEN, mainOrderFound.getPurpose());
    }

    @Test
    @DisplayName("Should get last hedge order")
    void testGetLastHedgeOrder() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Добавляем первый хедж
        Order hedgeOrder1 = Order.builder()
                .orderId(555666777L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("49000.00"))
                .commission(new BigDecimal("0.04"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder hedgeTradeOrder1 = new TradeOrder();
        hedgeTradeOrder1.onCreate(hedgeOrder1, new BigDecimal("-15.75"), SessionMode.HEDGING,
                "First hedge", tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN,
                123456789L, null);

        tradeSession.addOrder(hedgeTradeOrder1);

        // Добавляем второй хедж
        Order hedgeOrder2 = Order.builder()
                .orderId(666777888L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("48500.00"))
                .commission(new BigDecimal("0.04"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis() + 1000) // Более позднее время
                .build();

        TradeOrder hedgeTradeOrder2 = new TradeOrder();
        hedgeTradeOrder2.onCreate(hedgeOrder2, new BigDecimal("-25.00"), SessionMode.HEDGING,
                "Second hedge", tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN,
                123456789L, null);

        tradeSession.addOrder(hedgeTradeOrder2);

        // When
        TradeOrder lastHedgeOrder = tradeSession.getLastHedgeOrder();

        // Then
        assertNotNull(lastHedgeOrder);
        assertEquals(666777888L, lastHedgeOrder.getOrderId()); // Последний хедж
        assertEquals(OrderPurpose.HEDGE_OPEN, lastHedgeOrder.getPurpose());
    }

    @Test
    @DisplayName("Should change mode")
    void testChangeMode() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // When
        tradeSession.changeMode(SessionMode.HEDGING);

        // Then
        assertEquals(SessionMode.HEDGING, tradeSession.getCurrentMode());
        assertTrue(tradeSession.isInHedgeMode());
        assertFalse(tradeSession.isInScalpingMode());
    }

    @Test
    @DisplayName("Should handle null order in addOrder")
    void testAddNullOrder() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");
        int initialOrderCount = tradeSession.getOrders().size();

        // When
        tradeSession.addOrder(null);

        // Then
        assertEquals(initialOrderCount, tradeSession.getOrders().size());
        assertEquals(SessionMode.SCALPING, tradeSession.getCurrentMode());
        assertTrue(tradeSession.hasActivePosition());
    }

    @Test
    @DisplayName("Should handle unfilled order")
    void testUnfilledOrder() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Неисполненный ордер
        Order unfilledOrder = Order.builder()
                .orderId(111222333L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("LIMIT")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(BigDecimal.ZERO)
                .commission(BigDecimal.ZERO)
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.NEW)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder unfilledTradeOrder = new TradeOrder();
        unfilledTradeOrder.onCreate(unfilledOrder, BigDecimal.ZERO, SessionMode.SCALPING,
                "Unfilled order", tradePlan, TradingDirection.SHORT, OrderPurpose.MAIN_CLOSE,
                null, null);

        // When
        tradeSession.addOrder(unfilledTradeOrder);

        // Then
        assertEquals(2, tradeSession.getOrders().size());
        assertEquals(SessionMode.SCALPING, tradeSession.getCurrentMode()); // Режим не меняется
        assertTrue(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
        assertEquals(new BigDecimal("10.50"), tradeSession.getPnl()); // PnL не меняется
        assertEquals(new BigDecimal("0.05"), tradeSession.getTotalCommission()); // Комиссия не меняется
    }

    @Test
    @DisplayName("Should handle processing flag")
    void testProcessingFlag() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // When
        tradeSession.setProcessing(true);

        // Then
        assertTrue(tradeSession.isProcessing());

        // When
        tradeSession.setProcessing(false);

        // Then
        assertFalse(tradeSession.isProcessing());
    }

    @Test
    @DisplayName("Should handle session with multiple orders and complex PnL calculation")
    void testComplexSessionWithMultipleOrders() {
        // Given
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, mainOrder, "Test context");

        // Добавляем хедж
        Order hedgeOrder = Order.builder()
                .orderId(555666777L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("49000.00"))
                .commission(new BigDecimal("0.04"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder hedgeTradeOrder = new TradeOrder();
        hedgeTradeOrder.onCreate(hedgeOrder, new BigDecimal("-15.75"), SessionMode.HEDGING,
                "Hedge order", tradePlan, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN,
                123456789L, null);

        tradeSession.addOrder(hedgeTradeOrder);

        // Частично закрываем хедж
        Order partialCloseOrder = Order.builder()
                .orderId(444555666L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.0005"))
                .averagePrice(new BigDecimal("49500.00"))
                .commission(new BigDecimal("0.025"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder partialCloseTradeOrder = new TradeOrder();
        partialCloseTradeOrder.onCreate(partialCloseOrder, new BigDecimal("2.50"), SessionMode.HEDGING,
                "Partial close hedge", tradePlan, TradingDirection.LONG, OrderPurpose.HEDGE_PARTIAL_CLOSE,
                123456789L, 555666777L);

        tradeSession.addOrder(partialCloseTradeOrder);

        // Закрываем основную позицию
        Order closeMainOrder = Order.builder()
                .orderId(111222333L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("51000.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder closeMainTradeOrder = new TradeOrder();
        closeMainTradeOrder.onCreate(closeMainOrder, new BigDecimal("10.00"), SessionMode.SCALPING,
                "Close main order", tradePlan, TradingDirection.SHORT, OrderPurpose.MAIN_CLOSE,
                null, null);

        // When
        tradeSession.addOrder(closeMainTradeOrder);

        // Then
        assertEquals(SessionStatus.COMPLETED, tradeSession.getStatus());
        assertEquals(4, tradeSession.getOrders().size());
        assertFalse(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort()); // Все позиции закрыты
        assertFalse(tradeSession.hasActivePosition());
        assertEquals(new BigDecimal("7.25"), tradeSession.getPnl()); // 10.50 + (-15.75) + 2.50 + 10.00
        assertEquals(new BigDecimal("0.165"), tradeSession.getTotalCommission()); // 0.05 + 0.04 + 0.025 + 0.05
        assertEquals(1, tradeSession.getHedgeOpenCount());
        assertEquals(0, tradeSession.getHedgeCloseCount());
        assertNotNull(tradeSession.getEndTime());
        assertNotNull(tradeSession.getDurationMinutes());
    }

    @Test
    @DisplayName("Should handle session with zero values")
    void testSessionWithZeroValues() {
        // Given
        Order zeroOrder = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .commission(BigDecimal.ZERO)
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        TradeOrder zeroTradeOrder = new TradeOrder();
        zeroTradeOrder.onCreate(zeroOrder, BigDecimal.ZERO, SessionMode.SCALPING,
                "Zero order", tradePlan, TradingDirection.LONG, OrderPurpose.MAIN_OPEN,
                null, null);

        // When
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, zeroTradeOrder, "Test context");

        // Then
        assertEquals(BigDecimal.ZERO, tradeSession.getPnl());
        assertEquals(BigDecimal.ZERO, tradeSession.getTotalCommission());
        assertEquals(0, tradeSession.getHedgeOpenCount());
        assertEquals(0, tradeSession.getHedgeCloseCount());
        assertTrue(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
    }

    @Test
    @DisplayName("Should handle session with negative PnL")
    void testSessionWithNegativePnl() {
        // Given
        Order negativeOrder = Order.builder()
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

        TradeOrder negativeTradeOrder = new TradeOrder();
        negativeTradeOrder.onCreate(negativeOrder, new BigDecimal("-25.75"), SessionMode.SCALPING,
                "Negative order", tradePlan, TradingDirection.LONG, OrderPurpose.MAIN_OPEN,
                null, null);

        // When
        tradeSession.onCreate("BTCUSDT_plan", TradingDirection.LONG, negativeTradeOrder, "Test context");

        // Then
        assertEquals(new BigDecimal("-25.75"), tradeSession.getPnl());
        assertEquals(new BigDecimal("0.05"), tradeSession.getTotalCommission());
        assertTrue(tradeSession.isActiveLong());
        assertFalse(tradeSession.isActiveShort());
    }
} 
package io.cryptobot.binance.trade.session.model;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TradeSessionTest {

    private TradeSession session;
    private TradeOrder mainOrder;
    private TradePlan tradePlan;

    @BeforeEach
    void setUp() {
        session = new TradeSession();
        tradePlan = TradePlan.builder()
                .symbol("BTCUSDT")
                .leverage(10)
                .build();
        
        mainOrder = createOrder(1001L, OrderPurpose.MAIN_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(50000), BigDecimal.valueOf(0.1));
    }

    @Test
    void testSessionCreation() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");

        // Then
        assertEquals("BTCUSDT", session.getTradePlan());
        assertEquals(TradingDirection.LONG, session.getDirection());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode());
        assertEquals(1001L, session.getMainPosition());
        assertEquals(1, session.getOrders().size());
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());
        assertTrue(session.hasActivePosition());
        assertFalse(session.hasBothPositionsActive());
        assertNotNull(session.getCreatedTime());
    }

    @Test
    void testAddHedgeOrder() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);

        // When
        session.addOrder(hedgeOrder);

        // Then
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());
        assertEquals(2, session.getOrders().size());
        assertEquals(1, session.getHedgeOpenCount());
        assertEquals(0, session.getHedgeCloseCount());
    }

    @Test
    void testCloseMainOrder() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedgeOrder);
        
        TradeOrder closeMainOrder = createOrder(1003L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.1), 1001L, null);

        // When
        session.addOrder(closeMainOrder);

        // Then
        assertNull(session.getMainPosition()); // mainPosition очищен
        assertFalse(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertFalse(session.hasBothPositionsActive());
        assertTrue(session.hasActivePosition());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode()); // вернулись в SCALPING
        assertEquals(3, session.getOrders().size());
    }

    @Test
    void testCloseHedgeOrder() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedgeOrder);
        
        TradeOrder closeHedgeOrder = createOrder(1003L, OrderPurpose.HEDGE_CLOSE, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(48000), BigDecimal.valueOf(0.1), null, 1002L);

        // When
        session.addOrder(closeHedgeOrder);

        // Then
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());
        assertFalse(session.hasBothPositionsActive());
        assertTrue(session.hasActivePosition());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode()); // вернулись в SCALPING
        assertEquals(1, session.getHedgeCloseCount());
    }

    @Test
    void testComplexScenario() {
        // Given - создаем сессию с LONG
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        assertEquals(SessionMode.SCALPING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());

        // When - добавляем SHORT хедж
        TradeOrder hedge1 = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedge1);
        
        // Then - проверяем режим HEDGING
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());

        // When - закрываем MAIN LONG
        TradeOrder closeMain = createOrder(1003L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.1), 1001L, null);
        session.addOrder(closeMain);
        
        // Then - проверяем что остался только SHORT хедж
        assertNull(session.getMainPosition());
        assertFalse(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertFalse(session.hasBothPositionsActive());
        assertTrue(session.hasActivePosition());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode());

        // When - добавляем еще один LONG хедж (теперь у нас SHORT + LONG)
        TradeOrder hedge2 = createOrder(1004L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(52000), BigDecimal.valueOf(0.1), 1002L);
        session.addOrder(hedge2);
        
        // Then - проверяем что снова HEDGING режим
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());

        // When - закрываем SHORT хедж
        TradeOrder closeHedge1 = createOrder(1005L, OrderPurpose.HEDGE_CLOSE, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(48000), BigDecimal.valueOf(0.1), null, 1002L);
        session.addOrder(closeHedge1);
        
        // Then - проверяем что остался только LONG хедж
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());
        assertFalse(session.hasBothPositionsActive());
        assertTrue(session.hasActivePosition());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode());

        // When - закрываем последний LONG хедж
        TradeOrder closeHedge2 = createOrder(1006L, OrderPurpose.HEDGE_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(53000), BigDecimal.valueOf(0.1), null, 1004L);
        session.addOrder(closeHedge2);
        
        // Then - проверяем что сессия завершена
        assertFalse(session.isActiveLong());
        assertFalse(session.isActiveShort());
        assertFalse(session.hasActivePosition());
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertNotNull(session.getEndTime());
    }

    @Test
    void testPartialClose() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedgeOrder);
        
        // When - частичное закрытие MAIN
        TradeOrder partialCloseMain = createOrder(1003L, OrderPurpose.MAIN_PARTIAL_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.05), null, 1001L);
        session.addOrder(partialCloseMain);

        // Then - позиции должны остаться активными (частичное закрытие)
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
    }

    @Test
    void testNonFilledOrders() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        
        // When - добавляем неисполненный ордер
        TradeOrder pendingOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.NEW, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(pendingOrder);

        // Then - состояние не должно измениться
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());
        assertFalse(session.hasBothPositionsActive());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode());
    }

    @Test
    void testFindOrderById() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedgeOrder);

        // When & Then
        assertNotNull(session.findOrderById(1001L));
        assertNotNull(session.findOrderById(1002L));
        assertNull(session.findOrderById(9999L));
        
        assertEquals(mainOrder, session.getMainOrder());
        assertEquals(hedgeOrder, session.getLastHedgeOrder());
    }

    @Test
    void testModeChecks() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");

        // Then
        assertTrue(session.isInScalpingMode());
        assertFalse(session.isInHedgeMode());

        // When - добавляем хедж
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedgeOrder);

        // Then
        assertFalse(session.isInScalpingMode());
        assertTrue(session.isInHedgeMode());
    }

    @Test
    void testPnlCalculation() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        
        // When - добавляем ордер с PnL
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        hedgeOrder = hedgeOrder.toBuilder()
                .pnl(BigDecimal.valueOf(10.5))
                .commission(BigDecimal.valueOf(0.5))
                .build();
        session.addOrder(hedgeOrder);

        // Then
        assertEquals(BigDecimal.valueOf(10.5), session.getPnl());
        assertEquals(BigDecimal.valueOf(0.5), session.getTotalCommission());
    }

    @Test
    void testChangeMode() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");

        // When
        session.changeMode(SessionMode.HEDGING);

        // Then
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isInHedgeMode());
    }

    @Test
    void testCompleteSession() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");

        // When
        session.completeSession();

        // Then
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertNotNull(session.getEndTime());
        assertNotNull(session.getDurationMinutes());
    }

    @Test
    void testNullOrderHandling() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        int initialSize = session.getOrders().size();

        // When
        session.addOrder(null);

        // Then
        assertEquals(initialSize, session.getOrders().size());
    }

    @Test
    void testRecalcActiveFlags() {
        // Given - создаем сессию с LONG
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());

        // When - добавляем SHORT хедж
        TradeOrder hedgeOrder = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedgeOrder);

        // Then - проверяем что обе позиции активны
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());

        // When - закрываем MAIN
        TradeOrder closeMain = createOrder(1003L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.1), 1001L, null);
        session.addOrder(closeMain);

        // Then - проверяем что только SHORT активен
        assertFalse(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertFalse(session.hasBothPositionsActive());
        assertTrue(session.hasActivePosition());
    }

    // Вспомогательный метод для создания ордеров
    private TradeOrder createOrder(Long orderId, OrderPurpose purpose, TradingDirection direction, 
                                 OrderStatus status, BigDecimal price, BigDecimal quantity) {
        return createOrder(orderId, purpose, direction, status, price, quantity, null);
    }

    private TradeOrder createOrder(Long orderId, OrderPurpose purpose, TradingDirection direction, 
                                 OrderStatus status, BigDecimal price, BigDecimal quantity, Long parentOrderId) {
        return createOrder(orderId, purpose, direction, status, price, quantity, parentOrderId, null);
    }

    private TradeOrder createOrder(Long orderId, OrderPurpose purpose, TradingDirection direction, 
                                 OrderStatus status, BigDecimal price, BigDecimal quantity, Long parentOrderId, Long relatedHedgeId) {
        return TradeOrder.builder()
                .orderId(orderId)
                .purpose(purpose)
                .direction(direction)
                .status(status)
                .price(price)
                .count(quantity)
                .side(direction == TradingDirection.LONG ? OrderSide.BUY : OrderSide.SELL)
                .type("MARKET")
                .symbol("BTCUSDT")
                .orderTime(LocalDateTime.now())
                .parentOrderId(parentOrderId)
                .relatedHedgeId(relatedHedgeId)
                .build();
    }
} 
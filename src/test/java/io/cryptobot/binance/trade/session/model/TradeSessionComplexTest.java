package io.cryptobot.binance.trade.session.model;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TradeSessionComplexTest {

    private TradeSession session;
    private TradeOrder mainOrder;

    @BeforeEach
    void setUp() {
        session = new TradeSession();
        mainOrder = createOrder(1001L, OrderPurpose.MAIN_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(50000), BigDecimal.valueOf(0.1));
    }

    @Test
    void testMultipleHedgesScenario() {
        // Given - создаем сессию с LONG
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        assertEquals(SessionMode.SCALPING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());

        // When - добавляем первый SHORT хедж
        TradeOrder hedge1 = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedge1);
        
        // Then - проверяем режим HEDGING
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());

        // When - добавляем второй LONG хедж (теперь у нас LONG + SHORT + LONG)
        TradeOrder hedge2 = createOrder(1003L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(52000), BigDecimal.valueOf(0.1), 1002L);
        session.addOrder(hedge2);
        
        // Then - проверяем что все еще HEDGING (две позиции активны)
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());

        // When - закрываем первый SHORT хедж
        TradeOrder closeHedge1 = createOrder(1004L, OrderPurpose.HEDGE_CLOSE, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(48000), BigDecimal.valueOf(0.1), null, 1002L);
        session.addOrder(closeHedge1);
        
        // Then - проверяем что остались только LONG позиции
        assertTrue(session.isActiveLong());
        assertFalse(session.isActiveShort());
        assertFalse(session.hasBothPositionsActive());
        assertTrue(session.hasActivePosition());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode());

        // When - добавляем третий SHORT хедж
        TradeOrder hedge3 = createOrder(1005L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(53000), BigDecimal.valueOf(0.1), 1003L);
        session.addOrder(hedge3);
        
        // Then - проверяем что снова HEDGING
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());
    }

    @Test
    void testMainCloseWithMultipleHedges() {
        // Given - создаем сессию с LONG + SHORT хедж
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedge1 = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedge1);
        
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

        // When - добавляем новый LONG хедж к оставшемуся SHORT
        TradeOrder hedge2 = createOrder(1004L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(52000), BigDecimal.valueOf(0.1), 1002L);
        session.addOrder(hedge2);
        
        // Then - проверяем что снова HEDGING
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());
    }

    @Test
    void testPartialCloseScenario() {
        // Given - создаем сессию с LONG + SHORT хедж
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedge1 = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedge1);
        
        // When - частично закрываем MAIN
        TradeOrder partialCloseMain = createOrder(1003L, OrderPurpose.MAIN_PARTIAL_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.05), 1001L, null);
        session.addOrder(partialCloseMain);

        // Then - позиции должны остаться активными (частичное закрытие)
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());

        // When - частично закрываем SHORT хедж
        TradeOrder partialCloseHedge = createOrder(1004L, OrderPurpose.HEDGE_PARTIAL_CLOSE, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(48000), BigDecimal.valueOf(0.05), null, 1002L);
        session.addOrder(partialCloseHedge);

        // Then - позиции должны остаться активными
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
    }

    @Test
    void testSessionCompletion() {
        // Given - создаем сессию с LONG
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        
        // When - закрываем MAIN полностью
        TradeOrder closeMain = createOrder(1002L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.1), 1001L, null);
        session.addOrder(closeMain);
        
        // Then - сессия должна завершиться
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertFalse(session.hasActivePosition());
        assertNotNull(session.getEndTime());
        assertNotNull(session.getDurationMinutes());
    }

    @Test
    void testHedgeChainScenario() {
        // Given - создаем сессию с LONG
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        
        // When - добавляем цепочку хеджей: SHORT -> LONG -> SHORT -> LONG
        TradeOrder hedge1 = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedge1);
        
        TradeOrder hedge2 = createOrder(1003L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(52000), BigDecimal.valueOf(0.1), 1002L);
        session.addOrder(hedge2);
        
        TradeOrder hedge3 = createOrder(1004L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(53000), BigDecimal.valueOf(0.1), 1003L);
        session.addOrder(hedge3);
        
        TradeOrder hedge4 = createOrder(1005L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(54000), BigDecimal.valueOf(0.1), 1004L);
        session.addOrder(hedge4);
        
        // Then - проверяем что все еще HEDGING
        assertEquals(SessionMode.HEDGING, session.getCurrentMode());
        assertTrue(session.isActiveLong());
        assertTrue(session.isActiveShort());
        assertTrue(session.hasBothPositionsActive());
        assertEquals(4, session.getHedgeOpenCount());
        assertEquals(0, session.getHedgeCloseCount());

        // When - закрываем MAIN
        TradeOrder closeMain = createOrder(1006L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(55000), BigDecimal.valueOf(0.1), 1001L, null);
        session.addOrder(closeMain);
        
        // Then - проверяем что остались только хеджи
        assertNull(session.getMainPosition());
        // После закрытия MAIN LONG, флаг activeLong сбрасывается
        assertFalse(session.isActiveLong()); // MAIN LONG закрыт
        assertTrue(session.isActiveShort()); // SHORT хеджи остались активными
        assertFalse(session.hasBothPositionsActive());
        assertTrue(session.hasActivePosition());
        assertEquals(SessionMode.SCALPING, session.getCurrentMode()); // вернулись в SCALPING
        assertEquals(4, session.getHedgeOpenCount());
        assertEquals(0, session.getHedgeCloseCount());
    }

    @Test
    void testOrderFindingMethods() {
        // Given - создаем сессию с множественными ордерами
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        TradeOrder hedge1 = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        session.addOrder(hedge1);
        
        TradeOrder hedge2 = createOrder(1003L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(52000), BigDecimal.valueOf(0.1), 1002L);
        session.addOrder(hedge2);
        
        // When & Then - проверяем методы поиска ордеров
        assertNotNull(session.findOrderById(1001L));
        assertNotNull(session.findOrderById(1002L));
        assertNotNull(session.findOrderById(1003L));
        assertNull(session.findOrderById(9999L));
        
        assertEquals(mainOrder, session.getMainOrder());
        assertEquals(hedge2, session.getLastHedgeOrder()); // последний хедж
        
        // When - закрываем MAIN
        TradeOrder closeMain = createOrder(1004L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.1), 1001L, null);
        session.addOrder(closeMain);
        
        // Then - getMainOrder должен вернуть null
        assertNull(session.getMainOrder());
        assertEquals(hedge2, session.getLastHedgeOrder()); // последний хедж остается
    }

    @Test
    void testPnlCalculation() {
        // Given - создаем сессию с ордерами с PnL
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test");
        
        // When - добавляем ордера с PnL
        TradeOrder hedge1 = createOrder(1002L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, 
                OrderStatus.FILLED, BigDecimal.valueOf(49000), BigDecimal.valueOf(0.1), 1001L);
        hedge1 = hedge1.toBuilder()
                .pnl(BigDecimal.valueOf(10.5))
                .commission(BigDecimal.valueOf(0.5))
                .build();
        session.addOrder(hedge1);
        
        TradeOrder closeMain = createOrder(1003L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, 
                OrderStatus.FILLED, BigDecimal.valueOf(51000), BigDecimal.valueOf(0.1), 1001L, null);
        closeMain = closeMain.toBuilder()
                .pnl(BigDecimal.valueOf(5.2))
                .commission(BigDecimal.valueOf(0.3))
                .build();
        session.addOrder(closeMain);
        
        // Then - проверяем расчеты
        assertEquals(BigDecimal.valueOf(15.7), session.getPnl()); // 10.5 + 5.2
        assertEquals(BigDecimal.valueOf(0.8), session.getTotalCommission()); // 0.5 + 0.3
        assertEquals(1, session.getHedgeOpenCount());
        assertEquals(0, session.getHedgeCloseCount());
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
package io.cryptobot.binance.trading.monitoring.v3.help;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitorHelper Tests")
class MonitorHelperTest {

    @InjectMocks
    private MonitorHelper monitorHelper;

    private TradeSession session;
    private TradeOrder mainLongOrder;
    private TradeOrder mainShortOrder;
    private TradeOrder hedgeLongOrder;
    private TradeOrder hedgeShortOrder;
    private TradeOrder mainCloseOrder;
    private TradeOrder hedgeCloseOrder;

    @BeforeEach
    void setUp() {
        // Создаем ордера
        mainLongOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        mainShortOrder = TradeOrder.builder()
                .orderId(1002L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("51000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        hedgeLongOrder = TradeOrder.builder()
                .orderId(2001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("52000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        hedgeShortOrder = TradeOrder.builder()
                .orderId(2002L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("53000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now().plusMinutes(15))
                .build();

        mainCloseOrder = TradeOrder.builder()
                .orderId(3001L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.MAIN_CLOSE)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("54000"))
                .count(new BigDecimal("0.1"))
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(20))
                .build();

        hedgeCloseOrder = TradeOrder.builder()
                .orderId(3002L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_CLOSE)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("55000"))
                .count(new BigDecimal("0.1"))
                .parentOrderId(2001L)
                .orderTime(LocalDateTime.now().plusMinutes(25))
                .build();

        // Создаем сессию с LONG позицией
        session = new TradeSession();
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainLongOrder, "test context");
        session.setId("test-session");
    }

    @Test
    @DisplayName("Should get active order for monitoring - only LONG active")
    void testGetActiveOrderForMonitoringOnlyLong() {
        // Given
        // Сессия уже создана с LONG позицией в setUp()

        // When
        TradeOrder result = monitorHelper.getActiveOrderForMonitoring(session);

        // Then
        assertNotNull(result);
        assertEquals(1001L, result.getOrderId());
        assertEquals(TradingDirection.LONG, result.getDirection());
        assertEquals(OrderPurpose.MAIN_OPEN, result.getPurpose());
    }

    @Test
    @DisplayName("Should get active order for monitoring - both active")
    void testGetActiveOrderForMonitoringBothActive() {
        // Given
        session.addOrder(mainShortOrder); // Добавляем SHORT позицию

        // When
        TradeOrder result = monitorHelper.getActiveOrderForMonitoring(session);

        // Then
        assertNotNull(result);
        assertEquals(1001L, result.getOrderId()); // Main order
        assertEquals(OrderPurpose.MAIN_OPEN, result.getPurpose());
    }

    @Test
    @DisplayName("Should return null when no active positions")
    void testGetActiveOrderForMonitoringNoActive() {
        // Given
        // Создаем новую сессию без активных позиций
        TradeSession emptySession = new TradeSession();
        emptySession.setId("empty-session");

        // When
        TradeOrder result = monitorHelper.getActiveOrderForMonitoring(emptySession);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should get latest active order by direction - MAIN OPEN")
    void testGetLatestActiveOrderByDirectionMainOpen() {
        // Given
        session.addOrder(hedgeLongOrder);

        // When
        TradeOrder result = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(1001L, result.getOrderId());
        assertEquals(OrderPurpose.MAIN_OPEN, result.getPurpose());
    }

    @Test
    @DisplayName("Should get latest active order by direction - HEDGE OPEN when MAIN closed")
    void testGetLatestActiveOrderByDirectionHedgeWhenMainClosed() {
        // Given
        session.addOrder(hedgeLongOrder);
        session.addOrder(mainCloseOrder); // Закрываем MAIN

        // When
        TradeOrder result = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(2001L, result.getOrderId());
        assertEquals(OrderPurpose.HEDGE_OPEN, result.getPurpose());
    }

    @Test
    @DisplayName("Should get latest active order by direction - SHORT")
    void testGetLatestActiveOrderByDirectionShort() {
        // Given
        session.addOrder(mainShortOrder);
        session.addOrder(hedgeShortOrder);

        // When
        TradeOrder result = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT);

        // Then
        assertNotNull(result);
        assertEquals(1002L, result.getOrderId());
        assertEquals(TradingDirection.SHORT, result.getDirection());
        assertEquals(OrderPurpose.MAIN_OPEN, result.getPurpose());
    }

    @Test
    @DisplayName("Should return null when no active orders for direction")
    void testGetLatestActiveOrderByDirectionNoActive() {
        // Given
        session.addOrder(mainCloseOrder); // Закрываем единственный ордер

        // When
        TradeOrder result = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should get last filled hedge order by direction")
    void testGetLastFilledHedgeOrderByDirection() {
        // Given
        session.addOrder(hedgeLongOrder);
        session.addOrder(hedgeShortOrder);

        // When
        TradeOrder result = monitorHelper.getLastFilledHedgeOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(2001L, result.getOrderId());
        assertEquals(TradingDirection.LONG, result.getDirection());
        assertEquals(OrderPurpose.HEDGE_OPEN, result.getPurpose());
    }

    @Test
    @DisplayName("Should return null when no filled hedge orders for direction")
    void testGetLastFilledHedgeOrderByDirectionNoHedge() {
        // Given
        // Только MAIN ордер в сессии

        // When
        TradeOrder result = monitorHelper.getLastFilledHedgeOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should check if MAIN OPEN order is closed")
    void testIsOpenOrderClosedMainOpen() {
        // Given
        session.addOrder(mainCloseOrder);

        // When
        boolean result = monitorHelper.isOpenOrderClosed(session, mainLongOrder);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should check if HEDGE OPEN order is closed")
    void testIsOpenOrderClosedHedgeOpen() {
        // Given
        session.addOrder(hedgeLongOrder);
        session.addOrder(hedgeCloseOrder);

        // When
        boolean result = monitorHelper.isOpenOrderClosed(session, hedgeLongOrder);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should return false when MAIN OPEN order is not closed")
    void testIsOpenOrderClosedMainOpenNotClosed() {
        // Given
        // Нет закрывающего ордера

        // When
        boolean result = monitorHelper.isOpenOrderClosed(session, mainLongOrder);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false for unknown order purpose")
    void testIsOpenOrderClosedUnknownPurpose() {
        // Given
        TradeOrder unknownOrder = TradeOrder.builder()
                .orderId(9999L)
                .purpose(OrderPurpose.MAIN_CLOSE) // Не OPEN
                .build();

        // When
        boolean result = monitorHelper.isOpenOrderClosed(session, unknownOrder);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should check if main is still active - active")
    void testIsMainStillActiveTrue() {
        // Given
        // Сессия активна по умолчанию

        // When
        boolean result = monitorHelper.isMainStillActive(session);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should check if main is still active - closed")
    void testIsMainStillActiveClosed() {
        // Given
        session.addOrder(mainCloseOrder);

        // When
        boolean result = monitorHelper.isMainStillActive(session);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should check if direction is active by orders - active")
    void testIsDirectionActiveByOrdersTrue() {
        // Given
        // MAIN ордер уже добавлен в setUp()

        // When
        boolean result = monitorHelper.isDirectionActiveByOrders(session, TradingDirection.LONG);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should check if direction is active by orders - inactive")
    void testIsDirectionActiveByOrdersFalse() {
        // Given
        session.addOrder(mainCloseOrder);

        // When
        boolean result = monitorHelper.isDirectionActiveByOrders(session, TradingDirection.LONG);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should check if direction is active - by flag")
    void testIsDirectionActiveByFlag() {
        // Given
        // Сессия активна по умолчанию

        // When
        boolean result = monitorHelper.isDirectionActive(session, TradingDirection.LONG);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should determine close order purpose - MAIN_OPEN")
    void testDetermineCloseOrderPurposeMainOpen() {
        // Given
        TradeOrder mainOpen = TradeOrder.builder()
                .purpose(OrderPurpose.MAIN_OPEN)
                .build();

        // When
        OrderPurpose result = monitorHelper.determineCloseOrderPurpose(mainOpen);

        // Then
        assertEquals(OrderPurpose.MAIN_CLOSE, result);
    }

    @Test
    @DisplayName("Should determine close order purpose - HEDGE_OPEN")
    void testDetermineCloseOrderPurposeHedgeOpen() {
        // Given
        TradeOrder hedgeOpen = TradeOrder.builder()
                .purpose(OrderPurpose.HEDGE_OPEN)
                .build();

        // When
        OrderPurpose result = monitorHelper.determineCloseOrderPurpose(hedgeOpen);

        // Then
        assertEquals(OrderPurpose.HEDGE_CLOSE, result);
    }

    @Test
    @DisplayName("Should determine close order purpose - unknown")
    void testDetermineCloseOrderPurposeUnknown() {
        // Given
        TradeOrder unknown = TradeOrder.builder()
                .purpose(OrderPurpose.MAIN_CLOSE) // Не OPEN
                .build();

        // When
        OrderPurpose result = monitorHelper.determineCloseOrderPurpose(unknown);

        // Then
        assertEquals(OrderPurpose.HEDGE_CLOSE, result); // Fallback
    }

    @Test
    @DisplayName("Should create inflight key")
    void testInflightKey() {
        // Given
        String sessionId = "test-session";
        TradingDirection direction = TradingDirection.LONG;

        // When
        String result = monitorHelper.inflightKey(sessionId, direction);

        // Then
        assertEquals("test-session|LONG", result);
    }

    @Test
    @DisplayName("Should validate order - valid")
    void testIsValidOrderValid() {
        // Given
        TradeOrder validOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .price(new BigDecimal("50000"))
                .build();

        // When
        boolean result = monitorHelper.isValidOrder(validOrder);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should validate order - null order")
    void testIsValidOrderNull() {
        // When
        boolean result = monitorHelper.isValidOrder(null);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate order - null orderId")
    void testIsValidOrderNullOrderId() {
        // Given
        TradeOrder invalidOrder = TradeOrder.builder()
                .orderId(null)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .price(new BigDecimal("50000"))
                .build();

        // When
        boolean result = monitorHelper.isValidOrder(invalidOrder);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate order - zero price")
    void testIsValidOrderZeroPrice() {
        // Given
        TradeOrder invalidOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .price(BigDecimal.ZERO)
                .build();

        // When
        boolean result = monitorHelper.isValidOrder(invalidOrder);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate order for closing - valid")
    void testIsValidForClosingValid() {
        // Given
        TradeOrder validOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .price(new BigDecimal("50000"))
                .build();

        // When
        boolean result = monitorHelper.isValidForClosing(validOrder);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should validate order for closing - invalid purpose")
    void testIsValidForClosingInvalidPurpose() {
        // Given
        TradeOrder invalidOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_CLOSE) // Не OPEN
                .price(new BigDecimal("50000"))
                .build();

        // When
        boolean result = monitorHelper.isValidForClosing(invalidOrder);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate order for closing - invalid order")
    void testIsValidForClosingInvalidOrder() {
        // Given
        TradeOrder invalidOrder = TradeOrder.builder()
                .orderId(null) // Невалидный
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .price(new BigDecimal("50000"))
                .build();

        // When
        boolean result = monitorHelper.isValidForClosing(invalidOrder);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate session state - valid")
    void testIsSessionInValidStateValid() {
        // Given
        // Сессия уже валидна из setUp()

        // When
        boolean result = monitorHelper.isSessionInValidState(session);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should validate session state - null session")
    void testIsSessionInValidStateNull() {
        // When
        boolean result = monitorHelper.isSessionInValidState(null);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate session state - null id")
    void testIsSessionInValidStateNullId() {
        // Given
        TradeSession invalidSession = new TradeSession();
        // ID не установлен

        // When
        boolean result = monitorHelper.isSessionInValidState(invalidSession);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should get opposite direction - LONG to SHORT")
    void testOppositeLongToShort() {
        // When
        TradingDirection result = monitorHelper.opposite(TradingDirection.LONG);

        // Then
        assertEquals(TradingDirection.SHORT, result);
    }

    @Test
    @DisplayName("Should get opposite direction - SHORT to LONG")
    void testOppositeShortToLong() {
        // When
        TradingDirection result = monitorHelper.opposite(TradingDirection.SHORT);

        // Then
        assertEquals(TradingDirection.LONG, result);
    }

    @Test
    @DisplayName("Should handle null value in nvl")
    void testNvlNull() {
        // When
        BigDecimal result = monitorHelper.nvl(null);

        // Then
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("Should return value in nvl when not null")
    void testNvlNotNull() {
        // Given
        BigDecimal value = new BigDecimal("100.50");

        // When
        BigDecimal result = monitorHelper.nvl(value);

        // Then
        assertEquals(value, result);
    }

    @Test
    @DisplayName("Should handle NEW status orders")
    void testGetLatestActiveOrderByDirectionNewStatus() {
        // Given
        TradeOrder newOrder = TradeOrder.builder()
                .orderId(1003L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.NEW)
                .price(new BigDecimal("50000"))
                .orderTime(LocalDateTime.now().plusMinutes(30))
                .build();
        session.addOrder(newOrder);

        // When
        TradeOrder result = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(1003L, result.getOrderId());
        assertEquals(OrderStatus.NEW, result.getStatus());
    }

    @Test
    @DisplayName("Should handle multiple orders and return latest")
    void testGetLatestActiveOrderByDirectionMultipleOrders() {
        // Given
        TradeOrder olderOrder = TradeOrder.builder()
                .orderId(1004L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .orderTime(LocalDateTime.now())
                .build();
        
        TradeOrder newerOrder = TradeOrder.builder()
                .orderId(1005L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("51000"))
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();
        
        session.addOrder(olderOrder);
        session.addOrder(newerOrder);

        // When
        TradeOrder result = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(1005L, result.getOrderId()); // Новейший ордер
    }
}

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
import java.util.Optional;

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
    private TradeOrder avgLongOrder;
    private TradeOrder avgCloseLong;

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

        avgLongOrder = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.2"))
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(12))
                .build();

        avgCloseLong = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_CLOSE)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50500"))
                .count(new BigDecimal("0.2"))
                .parentOrderId(2100L)
                .orderTime(LocalDateTime.now().plusMinutes(40))
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
    @DisplayName("Averaging has priority over MAIN/HEDGE in latest-active selection")
    void testAveragingPriorityInLatestActive() {
        // Given
        session.addOrder(hedgeLongOrder);
        session.addOrder(avgLongOrder);

        // When
        TradeOrder result = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(OrderPurpose.AVERAGING_OPEN, result.getPurpose());
        assertEquals(2100L, result.getOrderId());
    }

    @Test
    @DisplayName("AVERAGING_OPEN is considered closed by AVERAGING_CLOSE with parent link")
    void testIsOpenOrderClosedForAveraging() {
        // Given
        session.addOrder(avgLongOrder);
        assertFalse(monitorHelper.isOpenOrderClosed(session, avgLongOrder));
        session.addOrder(avgCloseLong);

        // When
        boolean closed = monitorHelper.isOpenOrderClosed(session, avgLongOrder);

        // Then
        assertTrue(closed);
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

    @Test
    @DisplayName("canOpenAverageByDirection allows when flag false and blocks when true")
    void testCanOpenAverageByDirection() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, mainLongOrder, "ctx");
        // Initially no averaging flags
        assertTrue(monitorHelper.canOpenAverageByDirection(session, TradingDirection.LONG));
        // When open flag set
        session.openAverageLongPosition();
        assertFalse(monitorHelper.canOpenAverageByDirection(session, TradingDirection.LONG));
        // Short side independent
        assertTrue(monitorHelper.canOpenAverageByDirection(session, TradingDirection.SHORT));
        session.openAverageShortPosition();
        assertFalse(monitorHelper.canOpenAverageByDirection(session, TradingDirection.SHORT));
    }

    // ========== НОВЫЕ ТЕСТЫ ДЛЯ ОБНОВЛЕННОЙ ЛОГИКИ ==========

    @Test
    @DisplayName("HEDGE_OPEN should be considered closed when AVERAGING_CLOSE exists")
    void shouldConsiderHedgeOpenClosedWhenAveragingCloseExists() {
        // Given: HEDGE_OPEN -> AVERAGING_OPEN -> AVERAGING_CLOSE
        TradeOrder hedgeOpen = TradeOrder.builder()
                .orderId(2001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .parentOrderId(2001L) // ссылается на HEDGE_OPEN
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder averagingClose = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(2100L) // закрывает AVERAGING_OPEN
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, hedgeOpen, "test");
        testSession.addOrder(averagingOpen);
        testSession.addOrder(averagingClose);

        // When
        boolean isClosed = monitorHelper.isOpenOrderClosed(testSession, hedgeOpen);

        // Then
        assertTrue(isClosed, "HEDGE_OPEN should be considered closed when AVERAGING_CLOSE exists");
    }

    @Test
    @DisplayName("MAIN_OPEN should be considered closed when AVERAGING_CLOSE exists")
    void shouldConsiderMainOpenClosedWhenAveragingCloseExists() {
        // Given: MAIN_OPEN -> AVERAGING_OPEN -> AVERAGING_CLOSE
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L) // ссылается на MAIN_OPEN
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder averagingClose = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(2100L) // закрывает AVERAGING_OPEN
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainOpen, "test");
        testSession.addOrder(averagingOpen);
        testSession.addOrder(averagingClose);

        // When
        boolean isClosed = monitorHelper.isOpenOrderClosed(testSession, mainOpen);

        // Then
        assertTrue(isClosed, "MAIN_OPEN should be considered closed when AVERAGING_CLOSE exists");
    }

    @Test
    @DisplayName("HEDGE_OPEN should NOT be considered closed when AVERAGING_OPEN exists but no AVERAGING_CLOSE")
    void shouldNotConsiderHedgeOpenClosedWhenAveragingOpenExistsButNoClose() {
        // Given: HEDGE_OPEN -> AVERAGING_OPEN (без AVERAGING_CLOSE)
        TradeOrder hedgeOpen = TradeOrder.builder()
                .orderId(2001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .parentOrderId(2001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.addOrder(hedgeOpen);
        testSession.addOrder(averagingOpen);

        // When
        boolean isClosed = monitorHelper.isOpenOrderClosed(testSession, hedgeOpen);

        // Then
        assertFalse(isClosed, "HEDGE_OPEN should NOT be considered closed when only AVERAGING_OPEN exists");
    }

    @Test
    @DisplayName("isDirectionActiveByOrders should prioritize AVERAGING_OPEN over HEDGE_OPEN")
    void shouldPrioritizeAveragingOverHedgeInDirectionActiveByOrders() {
        // Given: MAIN_OPEN, HEDGE_OPEN, AVERAGING_OPEN (все активны)
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder hedgeOpen = TradeOrder.builder()
                .orderId(2001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.addOrder(mainOpen);
        testSession.addOrder(hedgeOpen);
        testSession.addOrder(averagingOpen);

        // When
        boolean isActive = monitorHelper.isDirectionActiveByOrders(testSession, TradingDirection.LONG);

        // Then
        assertTrue(isActive, "Direction should be active when AVERAGING_OPEN exists");
    }

    @Test
    @DisplayName("isDirectionActiveByOrders should return false when all orders are closed")
    void shouldReturnFalseWhenAllOrdersAreClosed() {
        // Given: MAIN_OPEN -> MAIN_CLOSE
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder mainClose = TradeOrder.builder()
                .orderId(3001L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.MAIN_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.addOrder(mainOpen);
        testSession.addOrder(mainClose);

        // When
        boolean isActive = monitorHelper.isDirectionActiveByOrders(testSession, TradingDirection.LONG);

        // Then
        assertFalse(isActive, "Direction should NOT be active when all orders are closed");
    }

    @Test
    @DisplayName("isMainStillActive should return false when averaging exists and is active")
    void shouldReturnFalseWhenMainHasActiveAveraging() {
        // Given: MAIN_OPEN -> AVERAGING_OPEN (активное)
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainOpen, "test");
        testSession.addOrder(averagingOpen);

        // When
        boolean isMainActive = monitorHelper.isMainStillActive(testSession);

        // Then
        assertFalse(isMainActive, "MAIN should NOT be active when AVERAGING_OPEN exists and is active");
    }

    @Test
    @DisplayName("isMainStillActive should return false when averaging is closed and position is closed")
    void shouldReturnFalseWhenMainHasClosedAveraging() {
        // Given: MAIN_OPEN -> AVERAGING_OPEN -> AVERAGING_CLOSE
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder averagingClose = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(2100L)
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainOpen, "test");
        testSession.addOrder(averagingOpen);
        testSession.addOrder(averagingClose);

        // Debug: проверим что происходит
        boolean isAveragingClosed = monitorHelper.isOpenOrderClosed(testSession, averagingOpen);
        System.out.println("DEBUG: isAveragingClosed = " + isAveragingClosed);
        
        Optional<TradeOrder> avgOverMain = monitorHelper.findLastAveragingOpenForParent(testSession, mainOpen);
        System.out.println("DEBUG: avgOverMain present = " + avgOverMain.isPresent());
        if (avgOverMain.isPresent()) {
            System.out.println("DEBUG: avgOverMain orderId = " + avgOverMain.get().getOrderId());
            boolean isAvgClosedInMethod = monitorHelper.isOpenOrderClosed(testSession, avgOverMain.get());
            System.out.println("DEBUG: isAvgClosedInMethod = " + isAvgClosedInMethod);
        }
        
        // Debug: проверим состояние сессии
        System.out.println("DEBUG: session.isActiveLong() = " + testSession.isActiveLong());
        System.out.println("DEBUG: session.isActiveShort() = " + testSession.isActiveShort());
        System.out.println("DEBUG: main.getDirection() = " + mainOpen.getDirection());
        System.out.println("DEBUG: session.getMainOrder() != null = " + (testSession.getMainOrder() != null));

        // When
        boolean isMainActive = monitorHelper.isMainStillActive(testSession);

        // Then
        // По новой логике: после закрытия усреднения позиция закрывается для новых ордеров,
        // но MAIN ордер остается активным для мониторинга
        assertFalse(isMainActive, "MAIN should NOT be active when AVERAGING_CLOSE closes the position");
    }

    @Test
    @DisplayName("getActiveOrderForMonitoring should return AVERAGING_OPEN when it exists and is active")
    void shouldReturnAveragingOpenWhenActiveForMonitoring() {
        // Given: MAIN_OPEN + AVERAGING_OPEN (активное)
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainOpen, "test");
        testSession.addOrder(averagingOpen);

        // When
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertNotNull(activeOrder);
        assertEquals(OrderPurpose.AVERAGING_OPEN, activeOrder.getPurpose());
        assertEquals(2100L, activeOrder.getOrderId());
    }

    @Test
    @DisplayName("getActiveOrderForMonitoring should return HEDGE_OPEN when MAIN is closed but HEDGE is active")
    void shouldReturnHedgeOpenWhenMainClosedButHedgeActive() {
        // Given: MAIN_OPEN -> MAIN_CLOSE + HEDGE_OPEN (активный)
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder mainClose = TradeOrder.builder()
                .orderId(3001L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.MAIN_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder hedgeOpen = TradeOrder.builder()
                .orderId(2001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainOpen, "test");
        testSession.addOrder(mainClose);
        testSession.addOrder(hedgeOpen);

        // When
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertNotNull(activeOrder);
        assertEquals(OrderPurpose.HEDGE_OPEN, activeOrder.getPurpose());
        assertEquals(2001L, activeOrder.getOrderId());
    }

    @Test
    @DisplayName("getActiveOrderForMonitoring should return null when no active orders")
    void shouldReturnNullWhenNoActiveOrdersForMonitoring() {
        // Given: MAIN_OPEN -> MAIN_CLOSE (все закрыто)
        TradeOrder mainOpen = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder mainClose = TradeOrder.builder()
                .orderId(3001L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.MAIN_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainOpen, "test");
        testSession.addOrder(mainClose);

        // When
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertNull(activeOrder, "Should return null when no active orders");
    }

    @Test
    @DisplayName("getActiveOrderForMonitoring should return main order when both directions active")
    void shouldReturnMainOrderWhenBothDirectionsActive() {
        // Given: MAIN_OPEN (LONG) + HEDGE_OPEN (SHORT) - обе активны
        TradeOrder mainLong = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder hedgeShort = TradeOrder.builder()
                .orderId(2001L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainLong, "test");
        testSession.addOrder(hedgeShort);

        // When
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertNotNull(activeOrder);
        assertEquals(OrderPurpose.MAIN_OPEN, activeOrder.getPurpose());
        assertEquals(1001L, activeOrder.getOrderId());
    }

    @Test
    @DisplayName("isOpenOrderClosed should handle null order gracefully")
    void shouldHandleNullOrderInIsOpenOrderClosed() {
        // Given
        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");

        // When
        boolean result = monitorHelper.isOpenOrderClosed(testSession, null);

        // Then
        assertFalse(result, "Should return false for null order");
    }

    @Test
    @DisplayName("isOpenOrderClosed should handle null purpose gracefully")
    void shouldHandleNullPurposeInIsOpenOrderClosed() {
        // Given
        TradeOrder orderWithNullPurpose = TradeOrder.builder()
                .orderId(1001L)
                .purpose(null)
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");

        // When
        boolean result = monitorHelper.isOpenOrderClosed(testSession, orderWithNullPurpose);

        // Then
        assertFalse(result, "Should return false for order with null purpose");
    }

    @Test
    @DisplayName("isAverageActive should return true when flag is set")
    void shouldReturnTrueWhenAverageFlagIsSet() {
        // Given
        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.openAverageLongPosition();

        // When
        boolean isActive = monitorHelper.isAverageActive(testSession, TradingDirection.LONG);

        // Then
        assertTrue(isActive, "Should return true when averaging flag is set");
    }

    @Test
    @DisplayName("isAverageActive should return true when averaging order exists and is not closed")
    void shouldReturnTrueWhenAveragingOrderExistsAndNotClosed() {
        // Given
        TradeOrder averagingOpen = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.addOrder(averagingOpen);

        // When
        boolean isActive = monitorHelper.isAverageActive(testSession, TradingDirection.LONG);

        // Then
        assertTrue(isActive, "Should return true when averaging order exists and is not closed");
    }

    @Test
    @DisplayName("getLatestActiveAverageByDirection should return latest averaging order")
    void shouldReturnLatestActiveAverageByDirection() {
        // Given: два AVERAGING_OPEN ордера
        TradeOrder averaging1 = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averaging2 = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.addOrder(averaging1);
        testSession.addOrder(averaging2);

        // When
        TradeOrder result = monitorHelper.getLatestActiveAverageByDirection(testSession, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(2101L, result.getOrderId(), "Should return the latest averaging order");
    }

    @Test
    @DisplayName("getLastFilledAveragingOrderByDirection should return latest filled averaging order")
    void shouldReturnLatestFilledAveragingOrderByDirection() {
        // Given: два AVERAGING_OPEN ордера
        TradeOrder averaging1 = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averaging2 = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.addOrder(averaging1);
        testSession.addOrder(averaging2);

        // When
        TradeOrder result = monitorHelper.getLastFilledAveragingOrderByDirection(testSession, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(2101L, result.getOrderId(), "Should return the latest filled averaging order");
    }

    @Test
    @DisplayName("shouldOpenAverageLongPosition_whenMainLongExists")
    void shouldOpenAverageLongPosition_whenMainLongExists() {
        // Given: MAIN_OPEN LONG + AVERAGING_OPEN LONG
        TradeOrder mainLong = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingLong = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.2"))
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("test-plan", TradingDirection.LONG, mainLong, "test-context");
        testSession.addOrder(averagingLong);

        // When
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertNotNull(activeOrder);
        assertEquals(OrderPurpose.AVERAGING_OPEN, activeOrder.getPurpose());
        assertEquals(2100L, activeOrder.getOrderId());
        assertEquals(new BigDecimal("0.2"), activeOrder.getCount());
    }

    @Test
    @DisplayName("shouldCloseCombinedPosition_whenAveragingCloseExecuted")
    void shouldCloseCombinedPosition_whenAveragingCloseExecuted() {
        // Given: MAIN_OPEN + AVERAGING_OPEN + AVERAGING_CLOSE
        TradeOrder mainLong = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingLong = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.2"))
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder averagingClose = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_CLOSE)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50500"))
                .count(new BigDecimal("0.3")) // 0.1 + 0.2 = 0.3
                .parentOrderId(2100L)
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("test-plan", TradingDirection.LONG, mainLong, "test-context");
        testSession.addOrder(averagingLong);
        testSession.addOrder(averagingClose);

        // When
        boolean isMainClosed = monitorHelper.isOpenOrderClosed(testSession, mainLong);
        boolean isAveragingClosed = monitorHelper.isOpenOrderClosed(testSession, averagingLong);
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertTrue(isMainClosed, "MAIN_OPEN should be considered closed when AVERAGING_CLOSE exists");
        assertTrue(isAveragingClosed, "AVERAGING_OPEN should be considered closed when AVERAGING_CLOSE exists");
        assertNull(activeOrder, "No active order should exist after averaging close");
    }

    @Test
    @DisplayName("shouldCalculateCorrectQuantities_whenAveragingCombined")
    void shouldCalculateCorrectQuantities_whenAveragingCombined() {
        // Given: MAIN_OPEN (0.1) + AVERAGING_OPEN (0.2) = Combined (0.3)
        TradeOrder mainLong = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averagingLong = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.2"))
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("test-plan", TradingDirection.LONG, mainLong, "test-context");
        testSession.addOrder(averagingLong);

        // When
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertNotNull(activeOrder);
        assertEquals(OrderPurpose.AVERAGING_OPEN, activeOrder.getPurpose());
        assertEquals(new BigDecimal("0.2"), activeOrder.getCount());
        
        // Проверяем что активный ордер - это усредняющий
        assertEquals(2100L, activeOrder.getOrderId());
    }

    @Test
    @DisplayName("shouldHandleMultipleAveragingOrders_whenSequentialAveraging")
    void shouldHandleMultipleAveragingOrders_whenSequentialAveraging() {
        // Given: MAIN_OPEN + AVERAGING_OPEN1 + AVERAGING_OPEN2
        TradeOrder mainLong = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averaging1 = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.2"))
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder averaging2 = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49000"))
                .count(new BigDecimal("0.3"))
                .parentOrderId(2100L) // ссылается на предыдущее усреднение
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("test-plan", TradingDirection.LONG, mainLong, "test-context");
        testSession.addOrder(averaging1);
        testSession.addOrder(averaging2);

        // When
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertNotNull(activeOrder);
        assertEquals(OrderPurpose.AVERAGING_OPEN, activeOrder.getPurpose());
        assertEquals(2101L, activeOrder.getOrderId()); // последний усредняющий ордер
        assertEquals(new BigDecimal("0.3"), activeOrder.getCount());
    }

    @Test
    @DisplayName("shouldCloseAllPositions_whenFinalAveragingCloseExecuted")
    void shouldCloseAllPositions_whenFinalAveragingCloseExecuted() {
        // Given: MAIN_OPEN + AVERAGING_OPEN1 + AVERAGING_OPEN2 + AVERAGING_CLOSE
        TradeOrder mainLong = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        TradeOrder averaging1 = TradeOrder.builder()
                .orderId(2100L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.2"))
                .parentOrderId(1001L)
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        TradeOrder averaging2 = TradeOrder.builder()
                .orderId(2101L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49000"))
                .count(new BigDecimal("0.3"))
                .parentOrderId(2100L)
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        TradeOrder averagingClose = TradeOrder.builder()
                .orderId(2102L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.AVERAGING_CLOSE)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50500"))
                .count(new BigDecimal("0.6")) // 0.1 + 0.2 + 0.3 = 0.6
                .parentOrderId(2101L)
                .orderTime(LocalDateTime.now().plusMinutes(15))
                .build();

        TradeSession testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate("test-plan", TradingDirection.LONG, mainLong, "test-context");
        testSession.addOrder(averaging1);
        testSession.addOrder(averaging2);
        testSession.addOrder(averagingClose);

        // When
        boolean isMainClosed = monitorHelper.isOpenOrderClosed(testSession, mainLong);
        boolean isAveraging1Closed = monitorHelper.isOpenOrderClosed(testSession, averaging1);
        boolean isAveraging2Closed = monitorHelper.isOpenOrderClosed(testSession, averaging2);
        TradeOrder activeOrder = monitorHelper.getActiveOrderForMonitoring(testSession);

        // Then
        assertTrue(isMainClosed, "MAIN_OPEN should be closed");
        assertTrue(isAveraging1Closed, "First AVERAGING_OPEN should be closed");
        assertTrue(isAveraging2Closed, "Second AVERAGING_OPEN should be closed");
        assertNull(activeOrder, "No active order should exist");
    }
}

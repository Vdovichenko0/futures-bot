package io.cryptobot.binance.trading.monitoring.v3;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import io.cryptobot.binance.trading.monitoring.v3.utils.CheckTrailing;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.configs.locks.TradeSessionLockRegistry;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MonitoringServiceV3Impl Tests")
class MonitoringServiceV3ImplTest {

    @Mock
    private MonitorHelper monitorHelper;

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private TradingUpdatesService tradingUpdatesService;

    @Mock
    private CheckTrailing checkTrailing;

    @Mock
    private TradeSessionLockRegistry lockRegistry;

    @InjectMocks
    private MonitoringServiceV3Impl monitoringService;

    private TradeSession session;
    private TradeOrder longOrder;
    private TradeOrder shortOrder;
    private ReentrantLock mockLock;
    private BigDecimal currentPrice;

    @BeforeEach
    void setUp() {
        session = new TradeSession();
        session.setId("test-session");

        longOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        shortOrder = TradeOrder.builder()
                .orderId(1002L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("51000"))
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        // Создаем сессию с правильным main order
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");

        mockLock = new ReentrantLock();
        currentPrice = new BigDecimal("52000");

        // Настройка моков
        when(lockRegistry.getLock(anyString())).thenReturn(mockLock);
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);
        when(monitorHelper.nvl(any(BigDecimal.class))).thenAnswer(invocation -> {
            BigDecimal value = invocation.getArgument(0);
            return value != null ? value : BigDecimal.ZERO;
        });
        when(monitorHelper.nvl(null)).thenReturn(BigDecimal.ZERO);
        when(monitorHelper.isSessionInValidState(any(TradeSession.class))).thenReturn(true);
        when(monitorHelper.isValidForClosing(any(TradeOrder.class))).thenReturn(true);
        when(monitorHelper.determineCloseOrderPurpose(any(TradeOrder.class))).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(monitorHelper.opposite(any(TradingDirection.class))).thenAnswer(invocation -> {
            TradingDirection dir = invocation.getArgument(0);
            return dir == TradingDirection.LONG ? TradingDirection.SHORT : TradingDirection.LONG;
        });
        when(monitorHelper.isDirectionActive(any(TradeSession.class), any(TradingDirection.class))).thenReturn(false);
        when(monitorHelper.isMainStillActive(any(TradeSession.class))).thenReturn(true);
        when(monitorHelper.getLastFilledHedgeOrderByDirection(any(TradeSession.class), any(TradingDirection.class))).thenReturn(null);
    }

    @Test
    @DisplayName("Should add session to monitoring")
    void testAddToMonitoring() {
        // When
        monitoringService.addToMonitoring(session);

        // Then
        // Проверяем косвенно через вызов monitor
        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        monitoringService.monitor();
        verify(monitorHelper).getActiveOrderForMonitoring(session);
    }

    @Test
    @DisplayName("Should remove session from monitoring")
    void testRemoveFromMonitoring() {
        // Given
        monitoringService.addToMonitoring(session);

        // When
        monitoringService.removeFromMonitoring(session.getId());

        // Then
        // Проверяем косвенно - после удаления сессия не должна обрабатываться
        monitoringService.monitor();
        verify(monitorHelper, never()).getActiveOrderForMonitoring(session);
    }

    @Test
    @DisplayName("Should not create hedge when both positions already active")
    void testShouldNotCreateHedgeWhenBothPositionsActive() {
        // Given
        session.addOrder(longOrder);
        session.addOrder(shortOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.isDirectionActive(session, TradingDirection.SHORT)).thenReturn(true);
        when(monitorHelper.isDirectionActive(session, TradingDirection.LONG)).thenReturn(true);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should not create duplicate orders for same direction")
    void testShouldNotCreateDuplicateOrdersForSameDirection() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.isDirectionActive(session, TradingDirection.LONG)).thenReturn(true);
        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle session lock busy")
    void testShouldHandleSessionLockBusy() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);
        
        // Лок занят
        mockLock.lock();

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());

        // Освобождаем лок
        mockLock.unlock();
    }

    @Test
    @DisplayName("Should skip processing when session is processing")
    void testShouldSkipProcessingWhenSessionIsProcessing() {
        // Given
        session.addOrder(longOrder);
        session.setProcessing(true);
        monitoringService.addToMonitoring(session);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should remove completed session from monitoring")
    void testShouldRemoveCompletedSessionFromMonitoring() {
        // Given
        session.completeSession(); // Используем метод для завершения сессии
        monitoringService.addToMonitoring(session);

        // When
        monitoringService.monitor();

        // Then
        // Проверяем косвенно - завершенная сессия не должна обрабатываться
        verify(monitorHelper, never()).getActiveOrderForMonitoring(session);
    }

    @Test
    @DisplayName("Should skip when price is null")
    void testShouldSkipWhenPriceIsNull() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);
        when(ticker24hService.getPrice(anyString())).thenReturn(null);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle single position trailing close")
    void testShouldHandleSinglePositionTrailingClose() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(true);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(session);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService).closePosition(
                eq(session), eq(SessionMode.SCALPING), eq(1001L),
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE),
                eq(currentPrice), contains("single_trailing")
        );
    }

    @Test
    @DisplayName("Should handle early hedge for single position")
    void testShouldHandleEarlyHedgeForSinglePosition() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);
        when(monitorHelper.isDirectionActive(session, TradingDirection.SHORT)).thenReturn(false);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(session);

        // PnL = -0.10% (ранний хедж)
        BigDecimal entryPrice = new BigDecimal("50000");
        BigDecimal currentPrice = new BigDecimal("49950"); // -0.10%
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService).openPosition(
                eq(session), eq(SessionMode.HEDGING), eq(TradingDirection.SHORT),
                eq(OrderPurpose.HEDGE_OPEN), eq(currentPrice),
                contains("early_hedge"), any(), isNull()
        );
    }

    @Test
    @DisplayName("Should handle two positions logic")
    void testShouldHandleTwoPositionsLogic() {
        // Given
        session.addOrder(longOrder);
        session.addOrder(shortOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT)).thenReturn(shortOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);

        // When
        monitoringService.monitor();

        // Then
        verify(monitorHelper).getLatestActiveOrderByDirection(session, TradingDirection.LONG);
        verify(monitorHelper).getLatestActiveOrderByDirection(session, TradingDirection.SHORT);
        verify(checkTrailing, times(2)).checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should close long position in two positions scenario")
    void testShouldCloseLongPositionInTwoPositionsScenario() {
        // Given
        session.addOrder(longOrder);
        session.addOrder(shortOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT)).thenReturn(shortOrder);
        when(checkTrailing.checkNewTrailing(eq(longOrder), any(BigDecimal.class))).thenReturn(true);
        when(checkTrailing.checkNewTrailing(eq(shortOrder), any(BigDecimal.class))).thenReturn(false);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(session);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService).closePosition(
                eq(session), eq(SessionMode.HEDGING), eq(1001L),
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE),
                eq(currentPrice), contains("two_pos_trailing_long")
        );
    }

    @Test
    @DisplayName("Should close short position in two positions scenario")
    void testShouldCloseShortPositionInTwoPositionsScenario() {
        // Given
        session.addOrder(longOrder);
        session.addOrder(shortOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT)).thenReturn(shortOrder);
        when(checkTrailing.checkNewTrailing(eq(longOrder), any(BigDecimal.class))).thenReturn(false);
        when(checkTrailing.checkNewTrailing(eq(shortOrder), any(BigDecimal.class))).thenReturn(true);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(session);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService).closePosition(
                eq(session), eq(SessionMode.HEDGING), eq(1002L),
                isNull(), eq(TradingDirection.SHORT), eq(OrderPurpose.HEDGE_CLOSE),
                eq(currentPrice), contains("two_pos_trailing_short")
        );
    }

    @Test
    @DisplayName("Should activate trailing for best position in two positions")
    void testShouldActivateTrailingForBestPositionInTwoPositions() {
        // Given
        session.addOrder(longOrder);
        session.addOrder(shortOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT)).thenReturn(shortOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);

        // PnL для LONG = +0.15% (выше порога 0.10%)
        BigDecimal entryPrice = new BigDecimal("50000");
        BigDecimal currentPrice = new BigDecimal("50075"); // +0.15%
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.monitor();

        // Then
        // Проверяем, что trailing активирован для лучшей позиции
        // Используем spy для проверки вызовов методов
        TradeOrder spyLongOrder = spy(longOrder);
        verify(spyLongOrder, times(1)).setTrailingActive(true);
        verify(spyLongOrder, times(1)).setPnlHigh(any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should handle exception in close position")
    void testShouldHandleExceptionInClosePosition() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(true);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Test exception"));

        // When
        monitoringService.monitor();

        // Then
        // Проверяем, что флаг processing сброшен после исключения
        assertFalse(session.isProcessing());
    }

    @Test
    @DisplayName("Should handle exception in open hedge")
    void testShouldHandleExceptionInOpenHedge() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);
        when(monitorHelper.isDirectionActive(session, TradingDirection.SHORT)).thenReturn(false);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Test exception"));

        // PnL = -0.10% (ранний хедж)
        BigDecimal entryPrice = new BigDecimal("50000");
        BigDecimal currentPrice = new BigDecimal("49950"); // -0.10%
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.monitor();

        // Then
        // Проверяем, что флаг processing сброшен после исключения
        assertFalse(session.isProcessing());
    }

    @Test
    @DisplayName("Should calculate PnL correctly for LONG position")
    void testShouldCalculatePnLCorrectlyForLongPosition() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);

        // PnL = +0.20% (50000 -> 50100)
        BigDecimal entryPrice = new BigDecimal("50000");
        BigDecimal currentPrice = new BigDecimal("50100"); // +0.20%
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.monitor();

        // Then
        // Проверяем, что PnL рассчитан правильно: (50100 - 50000) / 50000 * 100 = 0.20%
        verify(checkTrailing).checkNewTrailing(eq(longOrder), eq(new BigDecimal("0.20")));
    }

    @Test
    @DisplayName("Should calculate PnL correctly for SHORT position")
    void testShouldCalculatePnLCorrectlyForShortPosition() {
        // Given
        session.addOrder(shortOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(shortOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);

        // PnL = +0.20% (51000 -> 50880)
        BigDecimal entryPrice = new BigDecimal("51000");
        BigDecimal currentPrice = new BigDecimal("50880"); // +0.20%
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.monitor();

        // Then
        // Проверяем, что PnL рассчитан правильно: (51000 - 50880) / 51000 * 100 = 0.20%
        verify(checkTrailing).checkNewTrailing(eq(shortOrder), eq(new BigDecimal("0.20")));
    }

    @Test
    @DisplayName("Should handle null active order")
    void testShouldHandleNullActiveOrder() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(null);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle invalid order for closing")
    void testShouldHandleInvalidOrderForClosing() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(true);
        when(monitorHelper.isValidForClosing(longOrder)).thenReturn(false);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle invalid session state")
    void testShouldHandleInvalidSessionState() {
        // Given
        session.addOrder(longOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);
        when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(true);
        when(monitorHelper.isSessionInValidState(session)).thenReturn(false);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle null price in order")
    void testShouldHandleNullPriceInOrder() {
        // Given
        TradeOrder orderWithNullPrice = TradeOrder.builder()
                .orderId(1003L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(null)
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        session.addOrder(orderWithNullPrice);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(orderWithNullPrice);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle zero price in order")
    void testShouldHandleZeroPriceInOrder() {
        // Given
        TradeOrder orderWithZeroPrice = TradeOrder.builder()
                .orderId(1004L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(BigDecimal.ZERO)
                .count(new BigDecimal("0.1"))
                .orderTime(LocalDateTime.now())
                .build();

        session.addOrder(orderWithZeroPrice);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(orderWithZeroPrice);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle null orders in two positions")
    void testShouldHandleNullOrdersInTwoPositions() {
        // Given
        session.addOrder(longOrder);
        session.addOrder(shortOrder);
        monitoringService.addToMonitoring(session);

        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG)).thenReturn(null);
        when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT)).thenReturn(shortOrder);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle session not in monitoring")
    void testShouldHandleSessionNotInMonitoring() {
        // Given
        session.addOrder(longOrder);
        // НЕ добавляем в мониторинг

        when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longOrder);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }
}

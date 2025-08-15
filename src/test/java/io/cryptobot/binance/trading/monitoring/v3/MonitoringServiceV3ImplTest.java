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
import io.cryptobot.binance.trading.monitoring.v3.utils.CheckAveraging;
import io.cryptobot.binance.trading.monitoring.v3.utils.CheckTrailing;
import io.cryptobot.binance.trading.monitoring.v3.utils.ExtraClose;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceV3ImplTest {

    @Mock
    private MonitorHelper monitorHelper;
    
    @Mock
    private CheckAveraging averaging;
    
    @Mock
    private ExtraClose extraClose;
    
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

    private TradeSession testSession;
    private TradeOrder longOrder;
    private TradeOrder shortOrder;
    private ReentrantLock mockLock;

    @BeforeEach
    void setUp() {
        // Настройка блокировки
        mockLock = mock(ReentrantLock.class);
        lenient().when(lockRegistry.getLock(anyString())).thenReturn(mockLock);
        lenient().when(mockLock.tryLock()).thenReturn(true);

        // Создание тестовой сессии
        testSession = TradeSession.builder()
                .id("test-session")
                .tradePlan("BTCUSDT")
                .status(SessionStatus.ACTIVE)
                .currentMode(SessionMode.HEDGING)
                .direction(TradingDirection.LONG)
                .activeLong(true)
                .activeShort(true)
                .activeAverageLong(false)
                .activeAverageShort(false)
                .processing(false)
                .createdTime(LocalDateTime.now())
                .orders(new ArrayList<>())
                .build();

        // Создание LONG ордера
        longOrder = TradeOrder.builder()
                .orderId(12345L)
                .purpose(OrderPurpose.MAIN_OPEN)
                .direction(TradingDirection.LONG)
                .symbol("BTCUSDT")
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.001"))
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        // Создание SHORT ордера
        shortOrder = TradeOrder.builder()
                .orderId(12346L)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .direction(TradingDirection.SHORT)
                .symbol("BTCUSDT")
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.001"))
                .status(OrderStatus.FILLED)
                .orderTime(LocalDateTime.now())
                .build();

        testSession.getOrders().add(longOrder);
        testSession.getOrders().add(shortOrder);

        // Базовые моки для всех тестов
        lenient().when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("50000"));
        lenient().when(monitorHelper.getLatestActiveOrderByDirection(any(), any())).thenReturn(longOrder);
        lenient().when(monitorHelper.nvl(any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
    }

    private void waitForAsyncOperations() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("shouldAddSessionToMonitoring")
    void shouldAddSessionToMonitoring() {
        // When
        monitoringService.addToMonitoring(testSession);

        // Then
        assertNotNull(testSession);
    }

    @Test
    @DisplayName("shouldRemoveSessionFromMonitoring")
    void shouldRemoveSessionFromMonitoring() {
        // Given
        monitoringService.addToMonitoring(testSession);

        // When
        monitoringService.removeFromMonitoring(testSession.getId());

        // Then
        assertNotNull(testSession);
    }

    @Test
    @DisplayName("shouldHandleProcessingSession")
    void shouldHandleProcessingSession() {
        // Given
        testSession.setProcessing(true);
        monitoringService.addToMonitoring(testSession);

        // When
        monitoringService.monitor();

        // Then
        // Не должно вызывать никаких методов мониторинга
        verifyNoInteractions(ticker24hService);
    }

    @Test
    @DisplayName("shouldHandleCompletedSession")
    void shouldHandleCompletedSession() {
        // Given
        testSession.completeSession();
        monitoringService.addToMonitoring(testSession);

        // When
        monitoringService.monitor();

        // Then
        // Сессия должна быть удалена из мониторинга
        verifyNoInteractions(ticker24hService);
    }

    @Test
    @DisplayName("shouldHandleSessionWithoutActivePositions")
    void shouldHandleSessionWithoutActivePositions() {
        // Given
        testSession.closeLongPosition();
        testSession.closeShortPosition();
        monitoringService.addToMonitoring(testSession);

        lenient().when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("50000"));

        // When
        monitoringService.monitor();
        waitForAsyncOperations();

        // Then
        verify(ticker24hService, times(1)).getPrice("BTCUSDT");
        verifyNoInteractions(monitorHelper);
    }

    @Test
    @DisplayName("shouldHandleTwoPositionsLogic")
    void shouldHandleTwoPositionsLogic() {
        // Given
        monitoringService.addToMonitoring(testSession);
        lenient().when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("50000"));
        lenient().when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        lenient().when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        lenient().when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(checkTrailing.checkTrailing(any(), any())).thenReturn(false);
        lenient().when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        lenient().when(extraClose.checkExtraClose(any(), any(), any(), any())).thenReturn(false);

        // When
        monitoringService.monitor();
        waitForAsyncOperations();

        // Then
        verify(ticker24hService, times(1)).getPrice("BTCUSDT");
        verify(extraClose, times(1)).checkExtraClose(any(), any(), any(), any());
    }

    @Test
    @DisplayName("shouldHandleSinglePositionLogic")
    void shouldHandleSinglePositionLogic() {
        // Given
        testSession.closeShortPosition(); // Только LONG позиция
        monitoringService.addToMonitoring(testSession);
        lenient().when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("50000"));
        lenient().when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);

        // When
        monitoringService.monitor();
        waitForAsyncOperations();

        // Then
        verify(ticker24hService, times(1)).getPrice("BTCUSDT");
        verify(monitorHelper, times(1)).getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG));
    }

    @Test
    @DisplayName("shouldHandleNullPrice")
    void shouldHandleNullPrice() {
        // Given
        monitoringService.addToMonitoring(testSession);
        lenient().when(ticker24hService.getPrice("BTCUSDT")).thenReturn(null);

        // When
        monitoringService.monitor();
        waitForAsyncOperations();

        // Then
        verify(ticker24hService, times(1)).getPrice("BTCUSDT");
        verifyNoInteractions(monitorHelper);
    }

    @Test
    @DisplayName("shouldHandleLockedSession")
    void shouldHandleLockedSession() {
        // Given
        lenient().when(mockLock.tryLock()).thenReturn(false);
        monitoringService.addToMonitoring(testSession);

        // When
        monitoringService.monitor();

        // Then
        verifyNoInteractions(ticker24hService);
    }

    @Test
    @DisplayName("shouldHandleSessionNotInMonitoring")
    void shouldHandleSessionNotInMonitoring() {
        // Given
        lenient().when(mockLock.tryLock()).thenReturn(true);
        lenient().when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("50000"));

        // When
        monitoringService.monitor();

        // Then
        verifyNoInteractions(ticker24hService);
    }
}

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
import java.util.List;
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

    @BeforeEach
    void setUp() {
        // Создаем мок для блокировки
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock()).thenReturn(true);
        when(lockRegistry.getLock(anyString())).thenReturn(mockLock);

        // Создаем тестовую сессию
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

        // Создаем LONG ордер
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

        // Создаем SHORT ордер
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
    }

    @Test
    @DisplayName("shouldCloseOrderViaExtraClose_whenExtraCloseReturnsTrue")
    void shouldCloseOrderViaExtraClose_whenExtraCloseReturnsTrue() {
        // Given
        BigDecimal currentPrice = new BigDecimal("49000");
        
        // Настраиваем моки
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(checkTrailing.checkTrailing(any(), any())).thenReturn(false);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        
        // ExtraClose возвращает true - должно сработать экстра закрытие
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any())).thenReturn(true);
        
        when(tradingUpdatesService.closePosition(
                eq(testSession), 
                eq(SessionMode.HEDGING), 
                eq(12345L), 
                any(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.MAIN_CLOSE), 
                eq(currentPrice), 
                contains("extra_close")
        )).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, times(1)).closePosition(
                eq(testSession),
                eq(SessionMode.HEDGING),
                eq(12345L),
                any(),
                eq(TradingDirection.LONG),
                eq(OrderPurpose.MAIN_CLOSE),
                eq(currentPrice),
                contains("extra_close")
        );
    }

    @Test
    @DisplayName("shouldNotCloseOrderViaExtraClose_whenExtraCloseReturnsFalse")
    void shouldNotCloseOrderViaExtraClose_whenExtraCloseReturnsFalse() {
        // Given
        BigDecimal currentPrice = new BigDecimal("49000");
        
        // Настраиваем моки
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(checkTrailing.checkTrailing(any(), any())).thenReturn(false);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        
        // ExtraClose возвращает false - не должно сработать экстра закрытие
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any())).thenReturn(false);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, never()).closePosition(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                contains("extra_close")
        );
    }

    @Test
    @DisplayName("shouldCalculateCorrectPnLsForExtraClose")
    void shouldCalculateCorrectPnLsForExtraClose() {
        // Given
        BigDecimal currentPrice = new BigDecimal("49000");
        
        // Настраиваем моки
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(checkTrailing.checkTrailing(any(), any())).thenReturn(false);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        
        // ExtraClose должен быть вызван с правильными PnL
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any())).thenReturn(false);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем что ExtraClose был вызван с правильными параметрами
        verify(extraClose, times(1)).checkExtraClose(
                eq(testSession),
                any(BigDecimal.class), // bestPnl
                any(BigDecimal.class), // worstPnl  
                any(TradeOrder.class)  // best order
        );
        
        // Проверяем что PnL рассчитываются правильно:
        // LONG: (49000 - 50000) / 50000 * 100 = -2.0%
        // SHORT: (49500 - 49000) / 49500 * 100 = +1.01%
        // SHORT должен быть best, LONG должен быть worst
    }

    @Test
    @DisplayName("shouldHandleExtraCloseWithNullOrders")
    void shouldHandleExtraCloseWithNullOrders() {
        // Given
        BigDecimal currentPrice = new BigDecimal("49000");
        
        // Настраиваем моки - возвращаем null для ордеров
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(null);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(null);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Не должно упасть с исключением
        verify(extraClose, never()).checkExtraClose(any(), any(), any(), any());
    }

    @Test
    @DisplayName("shouldHandleExtraCloseWithProcessingSession")
    void shouldHandleExtraCloseWithProcessingSession() {
        // Given
        testSession.setProcessing(true);
        
        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Не должно вызывать ExtraClose для обрабатываемой сессии
        verify(extraClose, never()).checkExtraClose(any(), any(), any(), any());
    }

    @Test
    @DisplayName("shouldHandleExtraCloseWithCompletedSession")
    void shouldHandleExtraCloseWithCompletedSession() {
        // Given
        testSession = testSession.toBuilder().status(SessionStatus.COMPLETED).build();
        
        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Сессия должна быть удалена из мониторинга
        verify(extraClose, never()).checkExtraClose(any(), any(), any(), any());
    }

    @Test
    @DisplayName("shouldHandleExtraCloseWithNullPrice")
    void shouldHandleExtraCloseWithNullPrice() {
        // Given
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(null);
        
        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Не должно вызывать ExtraClose при отсутствии цены
        verify(extraClose, never()).checkExtraClose(any(), any(), any(), any());
    }

    @Test
    @DisplayName("shouldHandleExtraCloseException")
    void shouldHandleExtraCloseException() {
        // Given
        BigDecimal currentPrice = new BigDecimal("49000");
        
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(checkTrailing.checkTrailing(any(), any())).thenReturn(false);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        
        // ExtraClose бросает исключение
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any()))
                .thenThrow(new RuntimeException("Test exception"));

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Не должно упасть с исключением, должно быть обработано
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }
}

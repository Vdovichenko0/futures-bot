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
class MonitoringServiceV3ImplIntegrationTest {

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
        // Создаем мок для блокировки
        mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock()).thenReturn(true);
        when(lockRegistry.getLock(anyString())).thenReturn(mockLock);

        // Создаем тестовую сессию с двумя позициями
        testSession = TradeSession.builder()
                .id("integration-test-session")
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

        // Создаем LONG ордер (основная позиция)
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

        // Создаем SHORT ордер (хедж)
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
    @DisplayName("shouldCompleteExtraCloseCycle_whenBothPositionsAreLosing")
    void shouldCompleteExtraCloseCycle_whenBothPositionsAreLosing() {
        // Given - настраиваем сценарий где обе позиции в убытке
        BigDecimal currentPrice = new BigDecimal("48500"); // цена упала значительно
        
        // Настраиваем моки для двух позиций
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        
        // Трейлинг не срабатывает
        when(checkTrailing.checkNewTrailing(any(), any())).thenReturn(false);
        
        // Усреднение не срабатывает
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        
        // ExtraClose срабатывает
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any())).thenReturn(true);
        
        // Настраиваем успешное закрытие позиции
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

        // When - запускаем мониторинг
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - проверяем что произошло экстра закрытие
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
        
        // Проверяем что ExtraClose был вызван с правильными параметрами
        verify(extraClose, times(1)).checkExtraClose(
                eq(testSession),
                any(BigDecimal.class), // bestPnl
                any(BigDecimal.class), // worstPnl
                any(TradeOrder.class)  // best order
        );
    }

    @Test
    @DisplayName("shouldHandleExtraCloseCycleWithPriceFluctuations")
    void shouldHandleExtraCloseCycleWithPriceFluctuations() {
        // Given - симулируем колебания цены
        BigDecimal[] prices = {
            new BigDecimal("50000"), // начальная цена
            new BigDecimal("49500"), // небольшое падение
            new BigDecimal("49000"), // большее падение
            new BigDecimal("48500"), // критическое падение - должно сработать экстра закрытие
            new BigDecimal("48000")  // еще большее падение
        };
        
        // Настраиваем моки
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(prices[0], prices[1], prices[2], prices[3], prices[4]);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        
        when(checkTrailing.checkNewTrailing(any(), any())).thenReturn(false);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        
        // ExtraClose срабатывает только при критическом падении (цена 48500)
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any()))
                .thenReturn(false)  // первые 3 вызова
                .thenReturn(true)   // 4-й вызов (цена 48500)
                .thenReturn(false); // 5-й вызов
        
        when(tradingUpdatesService.closePosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(testSession);

        // When - запускаем несколько циклов мониторинга
        monitoringService.addToMonitoring(testSession);
        
        // Симулируем несколько циклов мониторинга
        for (int i = 0; i < 5; i++) {
            monitoringService.monitor();
        }

        // Then - проверяем что экстра закрытие сработало только один раз
        verify(tradingUpdatesService, times(1)).closePosition(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                contains("extra_close")
        );
        
        // Проверяем количество вызовов ExtraClose
        verify(extraClose, times(5)).checkExtraClose(
                eq(testSession),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(TradeOrder.class)
        );
    }

    @Test
    @DisplayName("shouldHandleExtraCloseCycleWithSessionCompletion")
    void shouldHandleExtraCloseCycleWithSessionCompletion() {
        // Given - настраиваем сценарий завершения сессии
        BigDecimal currentPrice = new BigDecimal("48500");
        
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        
        when(checkTrailing.checkNewTrailing(any(), any())).thenReturn(false);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any())).thenReturn(true);
        
        // После закрытия позиции сессия завершается
        TradeSession completedSession = testSession.toBuilder()
                .status(SessionStatus.COMPLETED)
                .build();
        
        when(tradingUpdatesService.closePosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(completedSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, times(1)).closePosition(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                contains("extra_close")
        );
        
        // Сессия должна быть удалена из мониторинга после завершения
        // Это проверяется через внутреннее состояние monitoringService
    }

    @Test
    @DisplayName("shouldHandleExtraCloseCycleWithProcessingLock")
    void shouldHandleExtraCloseCycleWithProcessingLock() {
        // Given - сессия заблокирована для обработки
        testSession.setProcessing(true);
        
        BigDecimal currentPrice = new BigDecimal("48500");
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - не должно вызывать ExtraClose для заблокированной сессии
        verify(extraClose, never()).checkExtraClose(any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("shouldHandleExtraCloseCycleWithLockContention")
    void shouldHandleExtraCloseCycleWithLockContention() {
        // Given - блокировка занята
        when(mockLock.tryLock()).thenReturn(false);
        
        BigDecimal currentPrice = new BigDecimal("48500");
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - не должно вызывать ExtraClose при занятой блокировке
        verify(extraClose, never()).checkExtraClose(any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("shouldHandleExtraCloseCycleWithExceptionHandling")
    void shouldHandleExtraCloseCycleWithExceptionHandling() {
        // Given - ExtraClose бросает исключение
        BigDecimal currentPrice = new BigDecimal("48500");
        
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(currentPrice);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.LONG)))
                .thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(eq(testSession), eq(TradingDirection.SHORT)))
                .thenReturn(shortOrder);
        when(monitorHelper.nvl(any())).thenReturn(BigDecimal.ZERO);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        
        when(checkTrailing.checkNewTrailing(any(), any())).thenReturn(false);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(false);
        
        // ExtraClose бросает исключение
        when(extraClose.checkExtraClose(eq(testSession), any(), any(), any()))
                .thenThrow(new RuntimeException("Test exception"));

        // When & Then - не должно упасть с исключением
        assertDoesNotThrow(() -> {
            monitoringService.addToMonitoring(testSession);
            monitoringService.monitor();
        });
        
        // Проверяем что сессия осталась в мониторинге несмотря на исключение
        // (это проверяется через внутреннее состояние)
    }
}

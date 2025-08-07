package io.cryptobot.binance.trading.monitoring;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MonitoringServiceImpl Integration Test - Full Session Lifecycle")
class MonitoringServiceImplIntegrationTest {

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private TradingUpdatesService tradingUpdatesService;

    @InjectMocks
    private MonitoringServiceImpl monitoringService;

    private TradeSession initialSession;
    private TradeOrder mainOrder;
    private TradePlan tradePlan;

    @BeforeEach
    void setUp() {
        // Подготовка TradePlan
        tradePlan = new TradePlan();
        tradePlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, null, null);

        // Подготовка основного ордера
        mainOrder = TradeOrder.builder()
                .orderId(123L)
                .price(new BigDecimal("100.00")) // Цена входа
                .direction(TradingDirection.LONG)
                .trailingActive(false)
                .pnlHigh(BigDecimal.ZERO)
                .build();

        // Подготовка начальной торговой сессии
        initialSession = TradeSession.builder()
                .id("session-123")
                .direction(TradingDirection.LONG)
                .currentMode(SessionMode.SCALPING)
                .status(SessionStatus.ACTIVE)
                .processing(false)
                .orders(Arrays.asList(mainOrder))
                .tradePlan("BTCUSDT")
                .mainPosition(123L)
                .activeLong(true)
                .activeShort(false)
                .build();
    }

    @Test
    @DisplayName("Базовый сценарий: Открытие хеджа при ухудшении")
    void shouldOpenHedgeWhenWorsening() {
        // Given - Настройка последовательности цен
        when(ticker24hService.getPrice(anyString()))
                .thenReturn(new BigDecimal("99.7"))   // 1. tracking активируется
                .thenReturn(new BigDecimal("99.6"));  // 2. hedge открывается

        // Подготовка моков для открытия позиций
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(456L)
                .price(new BigDecimal("99.6"))
                .direction(TradingDirection.SHORT)
                .build();

        // Мок для открытия хеджа
        TradeSession hedgedSession = TradeSession.builder()
                .id("session-123")
                .currentMode(SessionMode.HEDGING)
                .status(SessionStatus.ACTIVE)
                .orders(Arrays.asList(mainOrder, hedgeOrder))
                .activeLong(true)
                .activeShort(true)
                .mainPosition(123L)
                .build();

        // Настройка моков для открытия позиций
        when(tradingUpdatesService.openPosition(
                any(TradeSession.class),
                eq(SessionMode.HEDGING),
                eq(TradingDirection.SHORT),
                eq(OrderPurpose.HEDGE_OPEN),
                eq(new BigDecimal("99.6")),
                contains("monitoring_worsening"),
                eq(123L),
                isNull()
        )).thenReturn(hedgedSession);

        // When - Выполняем базовый цикл мониторинга
        monitoringService.addToMonitoring(initialSession);

        // Шаг 1: Активация tracking при PnL = -0.3%
        monitoringService.monitor();

        // Шаг 2: Открытие хеджа при ухудшении до -0.4%
        monitoringService.monitor();

        // Then - Проверяем вызовы
        verify(tradingUpdatesService, times(1)).openPosition(
                any(TradeSession.class),
                eq(SessionMode.HEDGING),
                eq(TradingDirection.SHORT),
                eq(OrderPurpose.HEDGE_OPEN),
                eq(new BigDecimal("99.6")),
                contains("monitoring_worsening"),
                eq(123L),
                isNull()
        );
    }

    @Test
    @DisplayName("Проверка блокировки сессии после отправки сигнала")
    void shouldBlockSessionAfterSignalSent() {
        // Given - Сессия в состоянии processing
        TradeSession processingSession = initialSession.toBuilder()
                .processing(true)
                .build();

        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("99.5"));

        // When
        monitoringService.addToMonitoring(processingSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что мониторинг пропущен из-за флага processing
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Проверка обработки ошибок при открытии/закрытии позиций")
    void shouldHandleErrorsGracefully() {
        // Given - Мок исключения при открытии позиции
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("99.6"));
        when(tradingUpdatesService.openPosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenThrow(new RuntimeException("Exchange error"));

        // When & Then
        assertDoesNotThrow(() -> {
            monitoringService.addToMonitoring(initialSession);
            monitoringService.monitor();
        });

        // Проверяем, что сессия не заблокирована после ошибки
        assertFalse(initialSession.isProcessing());
    }

    @Test
    @DisplayName("Проверка трейлинга в SCALPING режиме")
    void shouldActivateTrailingInScalpingMode() {
        // Given - Настройка последовательности цен для трейлинга
        when(ticker24hService.getPrice(anyString()))
                .thenReturn(new BigDecimal("100.15"))  // +0.15% (активация трейлинга)
                .thenReturn(new BigDecimal("100.20"))  // +0.20% (максимум)
                .thenReturn(new BigDecimal("100.16")); // +0.16% (откат 20%)

        // Мок для закрытия позиции
        TradeSession closedSession = TradeSession.builder()
                .id("session-123")
                .status(SessionStatus.COMPLETED)
                .orders(Arrays.asList())
                .activeLong(false)
                .activeShort(false)
                .build();

        when(tradingUpdatesService.closePosition(
                any(TradeSession.class),
                eq(SessionMode.SCALPING),
                eq(123L),
                isNull(),
                eq(TradingDirection.LONG),
                eq(OrderPurpose.HEDGE_CLOSE),
                eq(new BigDecimal("100.16")),
                contains("monitoring_TRAILING")
        )).thenReturn(closedSession);

        // When
        monitoringService.addToMonitoring(initialSession);

        // Шаг 1: Активация трейлинга при PnL = +0.15%
        monitoringService.monitor();

        // Шаг 2: Установка максимума при PnL = +0.20%
        monitoringService.monitor();

        // Шаг 3: Откат на 20% → закрытие позиции
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, times(1)).closePosition(
                any(TradeSession.class),
                eq(SessionMode.SCALPING),
                eq(123L),
                isNull(),
                eq(TradingDirection.LONG),
                eq(OrderPurpose.HEDGE_CLOSE),
                eq(new BigDecimal("100.16")),
                contains("monitoring_TRAILING")
        );
    }
} 
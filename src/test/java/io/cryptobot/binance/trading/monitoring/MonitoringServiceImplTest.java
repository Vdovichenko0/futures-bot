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
@DisplayName("MonitoringServiceImpl Tests")
class MonitoringServiceImplTest {

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private TradingUpdatesService tradingUpdatesService;

    @InjectMocks
    private MonitoringServiceImpl monitoringService;

    private TradeSession testSession;
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
                .price(new BigDecimal("50000.00")) // Цена входа
                .direction(TradingDirection.LONG)
                .trailingActive(false)
                .pnlHigh(BigDecimal.ZERO)
                .build();

        // Подготовка торговой сессии
        testSession = TradeSession.builder()
                .id("session-123")
                .direction(TradingDirection.LONG)
                .currentMode(SessionMode.SCALPING)
                .status(SessionStatus.ACTIVE)
                .processing(false)
                .orders(Arrays.asList(mainOrder))
                .tradePlan("BTCUSDT") // ID торгового плана
                .mainPosition(123L) // ID основного ордера
                .build();
    }

    @Test
    @DisplayName("Should monitor main order and close it when trailing activates")
    void testMonitorMainOrderAndCloseOnTrailing() {
        // Given - симуляция роста цены и отката для трейлинга
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal highPrice = new BigDecimal("50200.00"); // +0.4% максимум
        BigDecimal currentPrice = new BigDecimal("50160.00"); // +0.32% (откат от максимума)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        // Сначала устанавливаем высокую цену для активации трейлинга
        when(ticker24hService.getPrice(anyString())).thenReturn(highPrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor(); // Активирует трейлинг
        
        // Теперь устанавливаем цену с откатом для срабатывания трейлинга
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);
        
        // Мокаем успешное закрытие позиции
        TradeSession closedSession = TradeSession.builder()
                .id("session-123")
                .status(SessionStatus.COMPLETED)
                .build();
        
        when(tradingUpdatesService.closePosition(
                eq(testSession), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                eq(currentPrice), 
                contains("monitoring_TRAILING")
        )).thenReturn(closedSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что позиция была закрыта (трейлинг активировался при PnL > 0.15%)
        verify(tradingUpdatesService, times(1)).closePosition(
                eq(testSession), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                eq(currentPrice), 
                contains("monitoring_TRAILING")
        );
    }

    @Test
    @DisplayName("Should not close position when PnL is below trailing threshold")
    void testDoNotCloseWhenPnLBelowThreshold() {
        // Given - небольшая прибыль, недостаточная для трейлинга
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal currentPrice = new BigDecimal("50050.00"); // +0.1% рост (ниже 0.15%)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что трейлинг не активировался
        assertFalse(mainOrder.getTrailingActive());
        
        // Проверяем, что позиция не была закрыта
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle session processing flag correctly")
    void testSessionProcessingFlag() {
        // Given
        testSession = testSession.toBuilder().processing(true).build(); // Сессия уже обрабатывается
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("50100.00"));

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что мониторинг пропущен из-за флага processing
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle null price gracefully")
    void testHandleNullPrice() {
        // Given
        when(ticker24hService.getPrice(anyString())).thenReturn(null);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что мониторинг пропущен из-за null цены
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle null main order gracefully")
    void testHandleNullMainOrder() {
        // Given
        testSession = testSession.toBuilder().orders(Arrays.asList()).build(); // Убираем все ордера
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("50100.00"));

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что мониторинг пропущен из-за null основного ордера
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle exception during monitoring gracefully")
    void testHandleExceptionDuringMonitoring() {
        // Given
        when(ticker24hService.getPrice(anyString())).thenThrow(new RuntimeException("Test exception"));

        // When & Then
        assertDoesNotThrow(() -> {
            monitoringService.addToMonitoring(testSession);
            monitoringService.monitor();
        });
        
        // Проверяем, что исключение обработано корректно
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }
} 
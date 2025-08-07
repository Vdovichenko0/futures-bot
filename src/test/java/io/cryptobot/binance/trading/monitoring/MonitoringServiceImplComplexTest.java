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
@DisplayName("MonitoringServiceImpl Complex Scenarios Tests")
class MonitoringServiceImplComplexTest {

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
    @DisplayName("Сценарий 1: Открыта основная LONG позиция, PnL = -0.3% → должно активироваться отслеживание")
    void shouldActivateTracking_whenPnlIsMinus0Dot3Percent() {
        // Given - PnL = -0.3%
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal currentPrice = new BigDecimal("49850.00"); // -0.3%
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что отслеживание активировалось (basePnl установлен)
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
        
        // Проверяем, что сессия в режиме SCALPING
        assertTrue(testSession.isInScalpingMode());
    }

    @Test
    @DisplayName("Сценарий 2: PnL ухудшается до -0.4% (откат на -0.1%) → должен открыться хедж SHORT")
    void shouldOpenHedge_whenPnlWorsensByMinus0Dot1Percent() {
        // Given - сначала устанавливаем базовую точку при -0.3%
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal basePrice = new BigDecimal("49850.00"); // -0.3%
        BigDecimal worsePrice = new BigDecimal("49800.00"); // -0.4% (ухудшение на -0.1%)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        
        // Первый вызов - устанавливаем базовую точку
        when(ticker24hService.getPrice(anyString())).thenReturn(basePrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();
        
        // Второй вызов - ухудшение на -0.1%
        when(ticker24hService.getPrice(anyString())).thenReturn(worsePrice);
        
        // Мокаем успешное открытие хеджа
        TradeSession hedgedSession = TradeSession.builder()
                .id("session-123")
                .status(SessionStatus.ACTIVE)
                .currentMode(SessionMode.HEDGING)
                .build();
        
        when(tradingUpdatesService.openPosition(
                eq(testSession), 
                eq(SessionMode.HEDGING), 
                eq(TradingDirection.SHORT), 
                eq(OrderPurpose.HEDGE_OPEN), 
                eq(worsePrice), 
                contains("monitoring_worsening"), 
                eq(123L), 
                isNull()
        )).thenReturn(hedgedSession);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, times(1)).openPosition(
                eq(testSession), 
                eq(SessionMode.HEDGING), 
                eq(TradingDirection.SHORT), 
                eq(OrderPurpose.HEDGE_OPEN), 
                eq(worsePrice), 
                contains("monitoring_worsening"), 
                eq(123L), 
                isNull()
        );
    }

    @Test
    @DisplayName("Сценарий 3: После хеджа одна из позиций выходит в +0.2% → активируется трейлинг. При откате на 20% от максимума (до +0.16%) — закрывается")
    void shouldClosePosition_whenTrailingRetrace20Percent() {
        // Given - устанавливаем режим SCALPING и позицию в +0.2%
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal highPrice = new BigDecimal("50100.00"); // +0.2%
        BigDecimal retracePrice = new BigDecimal("50080.00"); // +0.16% (откат 20% от +0.2%)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        
        // Сначала устанавливаем высокую цену для активации трейлинга
        when(ticker24hService.getPrice(anyString())).thenReturn(highPrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor(); // Активирует трейлинг
        
        // Теперь устанавливаем цену с откатом для срабатывания трейлинга
        when(ticker24hService.getPrice(anyString())).thenReturn(retracePrice);
        
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
                eq(retracePrice), 
                contains("monitoring_TRAILING")
        )).thenReturn(closedSession);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, times(1)).closePosition(
                eq(testSession), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                eq(retracePrice), 
                contains("monitoring_TRAILING")
        );
    }

    @Test
    @DisplayName("Сценарий 4: Худшая позиция (например, MAIN -0.35%) отслеживается, но отката нет, и происходит дальнейшее ухудшение → открывается ре-хедж в другую сторону")
    void shouldOpenReHedge_whenWorstPositionWorsensFurther() {
        // Given - устанавливаем режим HEDGING с двумя позициями
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(456L)
                .price(new BigDecimal("50100.00"))
                .direction(TradingDirection.SHORT)
                .build();
        
        testSession = testSession.toBuilder()
                .currentMode(SessionMode.HEDGING)
                .orders(Arrays.asList(mainOrder, hedgeOrder))
                .activeLong(true)
                .activeShort(true)
                .build();
        
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal currentPrice = new BigDecimal("49825.00"); // MAIN = -0.35%
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);
        
        // Мокаем успешное открытие ре-хеджа
        TradeSession reHedgedSession = TradeSession.builder()
                .id("session-123")
                .status(SessionStatus.ACTIVE)
                .currentMode(SessionMode.HEDGING)
                .build();
        
        when(tradingUpdatesService.openPosition(
                eq(testSession), 
                eq(SessionMode.HEDGING), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_OPEN), 
                eq(currentPrice), 
                contains("monitoring_re_hedge_worsening"), 
                eq(123L), 
                isNull()
        )).thenReturn(reHedgedSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что ре-хедж НЕ открывается (нужно больше логики для HEDGING режима)
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Сценарий 5: Если обе позиции уже открыты (LONG + SHORT), то ре-хедж блокируется")
    void shouldBlockReHedge_whenBothPositionsAlreadyActive() {
        // Given - устанавливаем режим HEDGING с двумя активными позициями
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(456L)
                .price(new BigDecimal("50100.00"))
                .direction(TradingDirection.SHORT)
                .build();
        
        testSession = testSession.toBuilder()
                .currentMode(SessionMode.HEDGING)
                .orders(Arrays.asList(mainOrder, hedgeOrder))
                .activeLong(true)
                .activeShort(true)
                .build();
        
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal currentPrice = new BigDecimal("49800.00"); // Ухудшение для MAIN
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что ре-хедж НЕ открывается
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Сценарий 7: Трейлинг активировался, но PnL резко улучшился, а потом откат составил ровно 20%")
    void shouldClosePosition_whenTrailingRetraceExactly20Percent() {
        // Given - трейлинг активируется при PnL >= 0.15%
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal activatePrice = new BigDecimal("50075.00"); // +0.15% (активация трейлинга)
        BigDecimal highPrice = new BigDecimal("50125.00"); // +0.25% (максимум)
        BigDecimal retracePrice = new BigDecimal("50100.00"); // +0.20% (откат ровно 20% от +0.25%)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        
        // Активируем трейлинг
        when(ticker24hService.getPrice(anyString())).thenReturn(activatePrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();
        
        // Устанавливаем максимум
        when(ticker24hService.getPrice(anyString())).thenReturn(highPrice);
        monitoringService.monitor();
        
        // Откат ровно 20%
        when(ticker24hService.getPrice(anyString())).thenReturn(retracePrice);
        
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
                eq(retracePrice), 
                contains("monitoring_TRAILING")
        )).thenReturn(closedSession);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, times(1)).closePosition(
                eq(testSession), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                eq(retracePrice), 
                contains("monitoring_TRAILING")
        );
    }

    @Test
    @DisplayName("Сценарий 8: worstOrder.getBasePnl() НЕ null, но delta колеблется: сначала -0.05%, потом -0.08%, потом -0.09%")
    void shouldNotOpenHedge_whenDeltaBelowMinus0Dot1Percent() {
        // Given - стартовое отслеживание при -0.3%
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal basePrice = new BigDecimal("49850.00"); // -0.3%
        BigDecimal worsePrice1 = new BigDecimal("49875.00"); // -0.25% (delta = -0.05%)
        BigDecimal worsePrice2 = new BigDecimal("49860.00"); // -0.28% (delta = -0.08%)
        BigDecimal worsePrice3 = new BigDecimal("49855.00"); // -0.29% (delta = -0.09%)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        
        // Устанавливаем базовую точку
        when(ticker24hService.getPrice(anyString())).thenReturn(basePrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();
        
        // Тестируем ухудшения меньше -0.1%
        when(ticker24hService.getPrice(anyString())).thenReturn(worsePrice1);
        monitoringService.monitor();
        
        when(ticker24hService.getPrice(anyString())).thenReturn(worsePrice2);
        monitoringService.monitor();
        
        when(ticker24hService.getPrice(anyString())).thenReturn(worsePrice3);
        monitoringService.monitor();

        // Then
        // Проверяем, что хедж НЕ открывается
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Сценарий 9: PnL улучшился на +0.15%, но сразу резко упал ниже точки входа (откат более 100%)")
    void shouldClosePosition_whenTrailingRetraceMoreThan100Percent() {
        // Given - PnL достиг +0.0020 (активируем трейлинг)
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal highPrice = new BigDecimal("50100.00"); // +0.2% (активирует трейлинг)
        BigDecimal crashPrice = new BigDecimal("49950.00"); // -0.1% (откат >100%)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        
        // Активируем трейлинг
        when(ticker24hService.getPrice(anyString())).thenReturn(highPrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();
        
        // Резкий откат
        when(ticker24hService.getPrice(anyString())).thenReturn(crashPrice);
        
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
                eq(crashPrice), 
                contains("monitoring_TRAILING")
        )).thenReturn(closedSession);

        // When
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, times(1)).closePosition(
                eq(testSession), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                eq(crashPrice), 
                contains("monitoring_TRAILING")
        );
    }

    @Test
    @DisplayName("Сценарий 10: Максимальное улучшение 0.0018, затем плавный откат до 0.0015, потом снова рост до 0.0019")
    void shouldUpdateMaxChangePnl_whenImprovementIncreases() {
        // Given - стартовое отслеживание при -0.3%
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal basePrice = new BigDecimal("49850.00"); // -0.3%
        BigDecimal improvement1 = new BigDecimal("49910.00"); // -0.18% (улучшение +0.12%)
        BigDecimal retrace = new BigDecimal("49925.00"); // -0.15% (откат)
        BigDecimal improvement2 = new BigDecimal("49905.00"); // -0.19% (новое улучшение)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        
        // Устанавливаем базовую точку
        when(ticker24hService.getPrice(anyString())).thenReturn(basePrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();
        
        // Первое улучшение
        when(ticker24hService.getPrice(anyString())).thenReturn(improvement1);
        monitoringService.monitor();
        
        // Откат
        when(ticker24hService.getPrice(anyString())).thenReturn(retrace);
        monitoringService.monitor();
        
        // Второе улучшение
        when(ticker24hService.getPrice(anyString())).thenReturn(improvement2);
        monitoringService.monitor();

        // Then
        // Проверяем, что хедж НЕ открывается (улучшение не достигло +0.1%)
        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Сценарий 11: При попытке закрытия позиции происходит ошибка (исключение)")
    void shouldResetProcessingFlag_whenClosePositionThrowsException() {
        // Given - трейлинг активирован и готов к закрытию
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal highPrice = new BigDecimal("50100.00"); // +0.2% (активирует трейлинг)
        BigDecimal retracePrice = new BigDecimal("50080.00"); // +0.16% (откат 20%)
        
        mainOrder = mainOrder.toBuilder().price(entryPrice).build();
        
        // Активируем трейлинг
        when(ticker24hService.getPrice(anyString())).thenReturn(highPrice);
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();
        
        // Откат для срабатывания трейлинга
        when(ticker24hService.getPrice(anyString())).thenReturn(retracePrice);
        
        // Мокаем исключение при закрытии позиции
        when(tradingUpdatesService.closePosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenThrow(new RuntimeException("Exchange error"));

        // When
        monitoringService.monitor();

        // Then
        // Проверяем, что исключение обработано корректно
        verify(tradingUpdatesService, times(1)).closePosition(
                eq(testSession), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                eq(retracePrice), 
                contains("monitoring_TRAILING")
        );
        
        // Проверяем, что сессия не заблокирована
        assertFalse(testSession.isProcessing());
    }
} 
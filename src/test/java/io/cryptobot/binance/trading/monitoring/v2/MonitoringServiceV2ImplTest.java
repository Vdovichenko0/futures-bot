package io.cryptobot.binance.trading.monitoring.v2;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringServiceV2Impl Unit Tests")
class MonitoringServiceV2ImplTest {

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private TradingUpdatesService tradingUpdatesService;

    private MonitoringServiceV2Impl monitoringService;

    private static final String SYMBOL = "BTCUSDT";
    
    // === НОВЫЕ КОНСТАНТЫ ТЕСТИРОВАНИЯ (V2) ===
    // Константы для справки, могут использоваться в будущих тестах
    @SuppressWarnings("unused")
    private static final BigDecimal NEW_TRAILING_ACTIVATION_THRESHOLD = BigDecimal.valueOf(0.1);   // 0.1%
    @SuppressWarnings("unused")
    private static final BigDecimal NEW_TRAILING_CLOSE_RATIO = BigDecimal.valueOf(0.7);            // 30% откат (70% от макс)
    @SuppressWarnings("unused")
    private static final BigDecimal HEDGE_OPEN_THRESHOLD = BigDecimal.valueOf(-0.03);              // -0.03%
    @SuppressWarnings("unused")
    private static final BigDecimal PROFITABLE_POSITION_THRESHOLD = BigDecimal.valueOf(0.1);       // +0.1%

    private TradeSession testSession;
    private TradePlan testPlan;
    private TradeOrder mainOrder;

    @BeforeEach
    void setUp() {
        monitoringService = new MonitoringServiceV2Impl(sessionService, ticker24hService, tradingUpdatesService);

        // Создаем тестовый план
        testPlan = new TradePlan();
        testPlan.onCreate(SYMBOL, new BigDecimal("100"), 10, null, null);

        // Создаем основной ордер
        mainOrder = TradeOrder.builder()
                .orderId(12345L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.001"))
                .commission(new BigDecimal("0.5"))
                .orderTime(LocalDateTime.now())
                .trailingActive(false) // Важно! Инициализируем трейлинг как неактивный
                .build();

        // Создаем тестовую сессию
        testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate(SYMBOL, TradingDirection.LONG, mainOrder, "test context");
        
        // Мокаем базовые зависимости (lenient для избежания UnnecessaryStubbingException)
        lenient().when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("50000"));
        lenient().when(sessionService.getAllActive()).thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("Should activate new trailing at 0.1% PnL")
    void testNewTrailingActivation() {
        // Given
        BigDecimal currentPrice = new BigDecimal("50050"); // +0.1% от 50000
        when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Трейлинг должен активироваться при 0.1%
        TradeOrder activeOrder = testSession.getMainOrder();
        assertNotNull(activeOrder);
        assertTrue(activeOrder.getTrailingActive());
        assertEquals(0, NEW_TRAILING_ACTIVATION_THRESHOLD.compareTo(activeOrder.getPnlHigh()));
    }

    @Test
    @DisplayName("Should close position when trailing retraces 30%")
    void testNewTrailingClose() {
        // Given - активируем трейлинг сначала
        mainOrder = mainOrder.toBuilder()
                .trailingActive(true)
                .pnlHigh(new BigDecimal("0.2")) // Максимум 0.2%
                .build();
        
        // Пересоздаем сессию с новым ордером
        testSession = new TradeSession();
        testSession.setId("test-session");
        testSession.onCreate(SYMBOL, TradingDirection.LONG, mainOrder, "test context");

        // Цена падает до 70% от максимума (30% откат)
        BigDecimal retracePrice = new BigDecimal("50070"); // 0.14% (70% от 0.2%)
        when(ticker24hService.getPrice(anyString())).thenReturn(retracePrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService).closePosition(
                eq(testSession),
                eq(SessionMode.SCALPING),
                eq(mainOrder.getOrderId()),
                any(),
                eq(TradingDirection.LONG),
                eq(OrderPurpose.MAIN_CLOSE),
                any(BigDecimal.class),
                contains("new_monitoring_trailing")
        );
    }

    @Test
    @DisplayName("Should open hedge when loss reaches -0.03%")
    void testHedgeOpenOnLoss() {
        // Given
        BigDecimal lossPrice = new BigDecimal("49985"); // -0.03% от 50000
        when(ticker24hService.getPrice(anyString())).thenReturn(lossPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService).openPosition(
                eq(testSession),
                eq(SessionMode.HEDGING),
                eq(TradingDirection.SHORT), // Противоположное направление
                eq(OrderPurpose.HEDGE_OPEN),
                eq(lossPrice),
                contains("new_hedge_loss"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should close losing position when profitable reaches +0.1% in two positions mode")
    void testCloseLossingPositionOnProfit() {
        // Given - две активные позиции
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(12346L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.001"))
                .commission(new BigDecimal("0.5"))
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .trailingActive(false) // Инициализируем трейлинг
                .build();

        testSession.addOrder(hedgeOrder);
        testSession.openShortPosition(); // Активируем SHORT позицию

        // Цена растет, делая LONG прибыльным на +0.1%
        BigDecimal profitPrice = new BigDecimal("50050"); // +0.1% для LONG
        when(ticker24hService.getPrice(anyString())).thenReturn(profitPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - должен закрыть убыточную SHORT позицию
        verify(tradingUpdatesService).closePosition(
                eq(testSession),
                eq(SessionMode.HEDGING),
                eq(hedgeOrder.getOrderId()),
                any(),
                eq(TradingDirection.SHORT),
                eq(OrderPurpose.HEDGE_CLOSE),
                eq(profitPrice),
                contains("new_two_pos_logic")
        );
    }

    @Test
    @DisplayName("Should not open hedge when both positions are already active")
    void testPreventThirdPositionOpening() {
        // Given - две активные позиции
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(12346L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("49500"))
                .count(new BigDecimal("0.001"))
                .trailingActive(false) // Инициализируем трейлинг
                .build();

        testSession.addOrder(hedgeOrder);
        testSession.openShortPosition(); // Активируем обе позиции

        // Цена еще больше падает, что могло бы вызвать еще один хедж
        BigDecimal extremeLossPrice = new BigDecimal("49000"); // Большой убыток
        when(ticker24hService.getPrice(anyString())).thenReturn(extremeLossPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - НЕ должен открывать третью позицию
        verify(tradingUpdatesService, never()).openPosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should handle session processing flag correctly")
    void testSessionProcessingFlag() {
        // Given
        testSession.setProcessing(true); // Сессия уже обрабатывается
        BigDecimal anyPrice = new BigDecimal("50000");
        lenient().when(ticker24hService.getPrice(anyString())).thenReturn(anyPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - не должно быть никаких операций
        verify(tradingUpdatesService, never()).closePosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(tradingUpdatesService, never()).openPosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should handle null price gracefully")
    void testNullPriceHandling() {
        // Given
        when(ticker24hService.getPrice(anyString())).thenReturn(null);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - не должно быть исключений или операций
        verify(tradingUpdatesService, never()).closePosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(tradingUpdatesService, never()).openPosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should validate order types and directions before operations")
    void testOrderValidation() {
        // Given - ордер с неправильными данными
        TradeOrder invalidOrder = TradeOrder.builder()
                .orderId(null) // Invalid
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(BigDecimal.ZERO) // Invalid
                .trailingActive(false) // Инициализируем трейлинг
                .build();

        TradeSession invalidSession = new TradeSession();
        invalidSession.setId("invalid-session");
        invalidSession.onCreate(SYMBOL, TradingDirection.LONG, invalidOrder, "test");

        // When
        monitoringService.addToMonitoring(invalidSession);
        monitoringService.monitor();

        // Then - не должно быть операций с невалидными ордерами
        verify(tradingUpdatesService, never()).closePosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(tradingUpdatesService, never()).openPosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should prevent duplicate order directions")
    void testPreventDuplicateDirections() {
        // Given - пытаемся открыть хедж в том же направлении
        testSession.openLongPosition(); // LONG уже активен

        BigDecimal lossPrice = new BigDecimal("49985"); // Потеря
        when(ticker24hService.getPrice(anyString())).thenReturn(lossPrice);

        // Мокаем что система попытается открыть LONG хедж (неправильно)
        // Но наша защита должна это предотвратить

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - должен открыть только SHORT хедж, не LONG
        ArgumentCaptor<TradingDirection> directionCaptor = ArgumentCaptor.forClass(TradingDirection.class);
        verify(tradingUpdatesService).openPosition(
                any(), any(), directionCaptor.capture(), any(), any(), any(), any(), any()
        );

        assertEquals(TradingDirection.SHORT, directionCaptor.getValue());
    }

    @Test
    @DisplayName("Should handle completed sessions correctly")
    void testCompletedSessionHandling() {
        // Given
        testSession.completeSession(); // Завершаем сессию
        BigDecimal anyPrice = new BigDecimal("50000");
        when(ticker24hService.getPrice(anyString())).thenReturn(anyPrice);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - завершенные сессии не должны обрабатываться
        assertEquals(SessionStatus.COMPLETED, testSession.getStatus());
        // Но активных позиций нет, поэтому никаких операций не будет
        verify(tradingUpdatesService, never()).closePosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should maintain PnL high correctly during trending")
    void testPnlHighMaintenance() {
        // Given - цена растет постепенно
        List<BigDecimal> prices = List.of(
                new BigDecimal("50050"), // +0.1%
                new BigDecimal("50075"), // +0.15%
                new BigDecimal("50100"), // +0.2%
                new BigDecimal("50125")  // +0.25%
        );

        TradeOrder activeOrder = testSession.getMainOrder();
        
        // Добавляем сессию в мониторинг
        monitoringService.addToMonitoring(testSession);

        for (BigDecimal price : prices) {
            lenient().when(ticker24hService.getPrice(anyString())).thenReturn(price);
            
            // When
            monitoringService.monitor();
            
            // Then - PnL high должен обновляться
            BigDecimal expectedPnl = price.subtract(activeOrder.getPrice())
                    .divide(activeOrder.getPrice(), 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            // PnL high должен быть не меньше текущего PnL
            assertNotNull(activeOrder.getPnlHigh());
            assertTrue(activeOrder.getPnlHigh().compareTo(expectedPnl) >= 0);
        }
    }

    @Test
    @DisplayName("Should handle rapid price changes without accumulating positions")
    void testRapidPriceChangesHandling() {
        // Given - быстрые изменения цены
        List<BigDecimal> rapidPrices = List.of(
                new BigDecimal("49985"), // -0.03% (должен открыть хедж)
                new BigDecimal("49970"), // еще больше падение
                new BigDecimal("49980"), // небольшой рост
                new BigDecimal("49975")  // снова падение
        );

        // When - несколько циклов мониторинга подряд
        monitoringService.addToMonitoring(testSession);
        
        for (BigDecimal price : rapidPrices) {
            lenient().when(ticker24hService.getPrice(anyString())).thenReturn(price);
            monitoringService.monitor();
        }

        // Then - должен открыть только ОДИН хедж, не несколько
        verify(tradingUpdatesService, times(1)).openPosition(
                any(), any(), eq(TradingDirection.SHORT), any(), any(), any(), any(), any()
        );
    }
}

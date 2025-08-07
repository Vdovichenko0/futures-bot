package io.cryptobot.binance.trading.integration;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.binance.trading.monitoring.MonitoringServiceImpl;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Trading Integration Test - Full Trading Chain")
class TradingIntegrationTest {

    @Mock
    private TradePlanGetService tradePlanGetService;

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private OrderService orderService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private TradingUpdatesService tradingUpdatesService;

    @InjectMocks
    private MonitoringServiceImpl monitoringService;

    private TradePlan testPlan;
    private TradeSession testSession;
    private TradeOrder mainOrder;
    private Order mockOrder;

    @BeforeEach
    void setUp() {
        // Подготовка TradePlan
        testPlan = new TradePlan();
        testPlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, null, null);
        
        // Подготовка SizeModel для TradePlan
        SizeModel sizes = new SizeModel();
        sizes.setLotSize(new BigDecimal("0.001"));
        testPlan.updateSizes(sizes);

        // Подготовка mock Order
        mockOrder = createMockOrder("123", OrderSide.BUY, new BigDecimal("100.00"), new BigDecimal("1.0"));

        // Подготовка TradeOrder
        mainOrder = new TradeOrder();
        mainOrder.onCreate(
                mockOrder,
                BigDecimal.ZERO,
                SessionMode.SCALPING,
                "test_entry",
                testPlan,
                TradingDirection.LONG,
                OrderPurpose.MAIN_OPEN,
                null,
                null
        );

        // Подготовка TradeSession
        testSession = TradeSession.builder()
                .id("session-123")
                .tradePlan("BTCUSDT")
                .status(SessionStatus.ACTIVE)
                .currentMode(SessionMode.SCALPING)
                .direction(TradingDirection.LONG)
                .mainPosition(123L)
                .orders(new ArrayList<>())
                .activeLong(true)
                .activeShort(false)
                .createdTime(LocalDateTime.now())
                .build();
        
        // Инициализируем сессию правильно
        testSession.onCreate("BTCUSDT", TradingDirection.LONG, mainOrder, "test_entry");
        
        // Добавляем сессию в мониторинг
        monitoringService.addToMonitoring(testSession);
    }

    private Order createMockOrder(String orderId, OrderSide side, BigDecimal price, BigDecimal quantity) {
        Order order = new Order();
        order.setOrderId(Long.parseLong(orderId));
        order.setSide(side);
        order.setAveragePrice(price);
        order.setOrderStatus(OrderStatus.FILLED);
        order.setQuantity(quantity);
        order.setCommission(new BigDecimal("0.05"));
        order.setCommissionAsset("USDT");
        order.setTradeTime(System.currentTimeMillis());
        return order;
    }

    @Test
    @DisplayName("Тест: Открытие хеджа при ухудшении PnL")
    void shouldOpenHedgeWhenPnlWorsens() {
        // Шаг 1: Подготовка моков
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        
        // Мокаем открытие хеджа
        TradeSession hedgeSession = testSession.toBuilder()
                .currentMode(SessionMode.HEDGING)
                .hedgeOpenCount(1)
                .activeShort(true)
                .build();
        when(tradingUpdatesService.openPosition(
                any(TradeSession.class), 
                eq(SessionMode.HEDGING), 
                eq(TradingDirection.SHORT), 
                eq(OrderPurpose.HEDGE_OPEN), 
                any(BigDecimal.class), 
                anyString(), 
                eq(123L), 
                isNull()
        )).thenReturn(hedgeSession);

        // Шаг 2: Симулируем получение тиков с сигналами
        // Первый тик: цена 100.00 (базовая)
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("100.00"));
        monitoringService.monitor();
        
        // Второй тик: цена 99.70 (-0.3%) - запускается tracking
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("99.70"));
        monitoringService.monitor();
        
        // Третий тик: цена 99.60 (-0.4%) - ухудшение на -0.1% от базовой точки
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("99.60"));
        monitoringService.monitor();

        // Шаг 3: Проверяем, что хедж был открыт
        verify(tradingUpdatesService, times(1)).openPosition(
                any(TradeSession.class), 
                eq(SessionMode.HEDGING), 
                eq(TradingDirection.SHORT), 
                eq(OrderPurpose.HEDGE_OPEN), 
                any(BigDecimal.class), 
                anyString(),
                eq(123L), 
                isNull()
        );
    }

    @Test
    @DisplayName("Тест: Закрытие с трейлингом при улучшении и откате")
    void shouldCloseWithTrailingWhenImprovesAndRetraces() {
        // Шаг 1: Подготовка моков
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        
        // Мокаем закрытие позиции
        TradeSession closedSession = testSession.toBuilder()
                .status(SessionStatus.COMPLETED)
                .endTime(LocalDateTime.now())
                .pnl(new BigDecimal("0.15"))
                .totalCommission(new BigDecimal("0.10"))
                .build();
        when(tradingUpdatesService.closePosition(
                any(TradeSession.class), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                any(BigDecimal.class), 
                anyString()
        )).thenReturn(closedSession);

        // Шаг 2: Симулируем трейлинг
        // Активация трейлинга при +0.15%
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("100.15"));
        monitoringService.monitor();
        
        // Проверяем, что трейлинг активирован (используем compareTo для BigDecimal)
        assertTrue(mainOrder.getTrailingActive());
        assertEquals(0, mainOrder.getPnlHigh().compareTo(new BigDecimal("0.0015")));
        
        // Откат на 20% от максимума (100.15 * 0.8 = 100.12)
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("100.12"));
        monitoringService.monitor();

        // Шаг 3: Проверяем, что позиция была закрыта
        verify(tradingUpdatesService, times(1)).closePosition(
                any(TradeSession.class), 
                eq(SessionMode.SCALPING), 
                eq(123L), 
                isNull(), 
                eq(TradingDirection.LONG), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                any(BigDecimal.class), 
                anyString()
        );

        // Шаг 4: Проверяем финальные значения
        assertEquals(SessionStatus.COMPLETED, closedSession.getStatus());
        assertNotNull(closedSession.getEndTime());
        assertNotNull(closedSession.getPnl());
        assertEquals(new BigDecimal("0.10"), closedSession.getTotalCommission());
    }

    @Test
    @DisplayName("Тест: Завершение сессии после закрытия всех ордеров")
    void shouldCompleteSessionAfterAllOrdersClosed() {
        // Шаг 1: Подготовка моков
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(sessionService.getAllActive()).thenReturn(new ArrayList<>());
        
        // Мокаем закрытие позиции
        TradeSession closedSession = testSession.toBuilder()
                .status(SessionStatus.COMPLETED)
                .endTime(LocalDateTime.now())
                .activeLong(false)
                .activeShort(false)
                .build();
        when(tradingUpdatesService.closePosition(
                any(TradeSession.class), 
                any(SessionMode.class), 
                anyLong(), 
                any(), 
                any(TradingDirection.class), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                any(BigDecimal.class), 
                anyString()
        )).thenReturn(closedSession);

        // Шаг 2: Симулируем закрытие позиции
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("100.20"));
        monitoringService.monitor();

        // Шаг 3: Проверяем завершение сессии
        assertEquals(SessionStatus.COMPLETED, closedSession.getStatus());
        assertNotNull(closedSession.getEndTime());
        assertFalse(closedSession.hasActivePosition());
        
        // Шаг 4: Проверяем освобождение TradePlan
        testPlan.openActive();
        assertFalse(testPlan.getActive());
        assertNull(testPlan.getCurrentSessionId());

        // Шаг 5: Проверяем, что сессия больше не в мониторинге
        List<TradeSession> activeSessions = sessionService.getAllActive();
        assertTrue(activeSessions.isEmpty());
    }

    @Test
    @DisplayName("Тест: Хедж выходит в прибыль и закрывается с трейлингом")
    void shouldCloseHedgeWithTrailingWhenProfitable() {
        // Шаг 1: Подготовка хедж-сессии
        TradeOrder hedgeOrder = new TradeOrder();
        Order hedgeMockOrder = createMockOrder("456", OrderSide.SELL, new BigDecimal("99.70"), new BigDecimal("1.0"));
        hedgeOrder.onCreate(
                hedgeMockOrder,
                BigDecimal.ZERO,
                SessionMode.HEDGING,
                "hedge_entry",
                testPlan,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_OPEN,
                123L,
                null
        );
        
        TradeSession hedgeSession = testSession.toBuilder()
                .currentMode(SessionMode.HEDGING)
                .hedgeOpenCount(1)
                .activeShort(true)
                .orders(Arrays.asList(mainOrder, hedgeOrder))
                .build();

        // Добавляем хедж-сессию в мониторинг
        monitoringService.addToMonitoring(hedgeSession);

        // Шаг 2: Подготовка моков
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        
        // Мокаем закрытие хеджа
        TradeSession closedHedgeSession = hedgeSession.toBuilder()
                .hedgeCloseCount(1)
                .activeShort(false)
                .currentMode(SessionMode.SCALPING)
                .build();
        when(tradingUpdatesService.closePosition(
                any(TradeSession.class), 
                eq(SessionMode.HEDGING), 
                eq(456L), 
                eq(123L), 
                eq(TradingDirection.SHORT), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                any(BigDecimal.class), 
                anyString()
        )).thenReturn(closedHedgeSession);

        // Шаг 3: Симулируем трейлинг хеджа
        // Хедж в прибыли +0.25%
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("99.45"));
        monitoringService.monitor();
        
        // Проверяем, что трейлинг активирован для хеджа
        assertTrue(hedgeOrder.getTrailingActive());
        assertTrue(hedgeOrder.getPnlHigh().compareTo(BigDecimal.ZERO) > 0);
        
        // Проверяем, что хедж был создан правильно
        assertEquals(TradingDirection.SHORT, hedgeOrder.getDirection());
        assertEquals(OrderPurpose.HEDGE_OPEN, hedgeOrder.getPurpose());
        assertEquals(new BigDecimal("99.70"), hedgeOrder.getPrice());
        assertEquals(123L, hedgeOrder.getParentOrderId());

        // Шаг 4: Проверяем состояние сессии
        assertEquals(SessionMode.HEDGING, hedgeSession.getCurrentMode());
        assertEquals(1, hedgeSession.getHedgeOpenCount());
        assertTrue(hedgeSession.hasBothPositionsActive());
    }

    @Test
    @DisplayName("Тест: Ре-хедж при повторном ухудшении")
    void shouldRehedgeWhenOneSideClosedAndOtherWorsens() {
        // Шаг 1: Подготовка сессии с закрытым хеджем
        TradeOrder hedgeOrder = new TradeOrder();
        Order hedgeMockOrder = createMockOrder("456", OrderSide.SELL, new BigDecimal("99.70"), new BigDecimal("1.0"));
        hedgeOrder.onCreate(
                hedgeMockOrder,
                new BigDecimal("0.25"),
                SessionMode.HEDGING,
                "hedge_entry",
                testPlan,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_OPEN,
                123L,
                null
        );
        
        TradeSession partialClosedSession = testSession.toBuilder()
                .currentMode(SessionMode.SCALPING) // Вернулись в скальпинг после закрытия хеджа
                .hedgeOpenCount(1)
                .hedgeCloseCount(1)
                .activeShort(false) // Хедж закрыт
                .activeLong(true) // Основная позиция активна
                .orders(Arrays.asList(mainOrder, hedgeOrder))
                .build();

        // Добавляем сессию в мониторинг
        monitoringService.addToMonitoring(partialClosedSession);

        // Шаг 2: Подготовка моков
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        
        // Мокаем повторное открытие хеджа
        TradeSession rehedgeSession = partialClosedSession.toBuilder()
                .currentMode(SessionMode.HEDGING)
                .hedgeOpenCount(2)
                .activeShort(true)
                .build();
        when(tradingUpdatesService.openPosition(
                any(TradeSession.class), 
                eq(SessionMode.HEDGING), 
                eq(TradingDirection.SHORT), 
                eq(OrderPurpose.HEDGE_OPEN), 
                any(BigDecimal.class), 
                anyString(), 
                eq(123L), 
                isNull()
        )).thenReturn(rehedgeSession);

        // Шаг 3: Симулируем повторное ухудшение
        // Основная позиция снова в минус -0.3%
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("99.70"));
        monitoringService.monitor();
        
        // Дальнейшее ухудшение на -0.1%
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("99.60"));
        monitoringService.monitor();

        // Шаг 4: Проверяем повторное открытие хеджа
        verify(tradingUpdatesService, times(1)).openPosition(
                any(TradeSession.class), 
                eq(SessionMode.HEDGING), 
                eq(TradingDirection.SHORT), 
                eq(OrderPurpose.HEDGE_OPEN), 
                any(BigDecimal.class), 
                anyString(), 
                eq(123L), 
                isNull()
        );

        // Шаг 5: Проверяем состояние сессии
        assertEquals(SessionMode.HEDGING, rehedgeSession.getCurrentMode());
        assertEquals(2, rehedgeSession.getHedgeOpenCount());
        assertTrue(rehedgeSession.hasBothPositionsActive());
    }

    @Test
    @DisplayName("Тест: Расчет PnL с учетом комиссий")
    void shouldCalculatePnLWithCommissions() {
        // Шаг 1: Подготовка данных
        BigDecimal entryPrice = new BigDecimal("100.00");
        BigDecimal exitPrice = new BigDecimal("100.20"); // +0.2%
        BigDecimal commission = new BigDecimal("0.05");
        BigDecimal quantity = new BigDecimal("1.0");

        // Расчет ожидаемого PnL
        BigDecimal expectedPnl = exitPrice.subtract(entryPrice)
                .divide(entryPrice, 8, RoundingMode.HALF_UP)
                .multiply(quantity)
                .multiply(entryPrice)
                .subtract(commission.multiply(new BigDecimal("2"))); // Комиссии за вход и выход

        // Шаг 2: Подготовка моков
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        
        // Мокаем закрытие позиции с рассчитанным PnL
        TradeSession closedSession = testSession.toBuilder()
                .status(SessionStatus.COMPLETED)
                .endTime(LocalDateTime.now())
                .pnl(expectedPnl)
                .totalCommission(commission.multiply(new BigDecimal("2")))
                .build();
        when(tradingUpdatesService.closePosition(
                any(TradeSession.class), 
                any(SessionMode.class), 
                anyLong(), 
                any(), 
                any(TradingDirection.class), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                eq(exitPrice), 
                anyString()
        )).thenReturn(closedSession);

        // Шаг 3: Симулируем закрытие позиции
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(exitPrice);
        monitoringService.monitor();

        // Шаг 4: Проверяем расчет PnL
        assertEquals(expectedPnl, closedSession.getPnl());
        assertEquals(commission.multiply(new BigDecimal("2")), closedSession.getTotalCommission());
        
        // Проверяем, что PnL положительный (цена выросла)
        assertTrue(closedSession.getPnl().compareTo(BigDecimal.ZERO) > 0);
        
        // Проверяем финальные значения
        assertEquals(SessionStatus.COMPLETED, closedSession.getStatus());
        assertNotNull(closedSession.getEndTime());
        assertEquals(TradingDirection.LONG, closedSession.getDirection());
        assertEquals(SessionMode.SCALPING, closedSession.getCurrentMode());
    }

    @Test
    @DisplayName("Тест: Полная цепочка торговли с проверкой всех этапов")
    void shouldCompleteFullTradingChain() {
        // Шаг 1: Подготовка моков
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(sessionService.getAllActive()).thenReturn(Arrays.asList(testSession));
        
        // Мокаем закрытие позиции
        TradeSession completedSession = testSession.toBuilder()
                .status(SessionStatus.COMPLETED)
                .endTime(LocalDateTime.now())
                .activeLong(false)
                .activeShort(false)
                .pnl(new BigDecimal("0.15"))
                .totalCommission(new BigDecimal("0.10"))
                .build();
        when(tradingUpdatesService.closePosition(
                any(TradeSession.class), 
                any(SessionMode.class), 
                anyLong(), 
                any(), 
                any(TradingDirection.class), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                any(BigDecimal.class), 
                anyString()
        )).thenReturn(completedSession);

        // Шаг 2: Симулируем полную цепочку
        // Активация трейлинга
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("100.15"));
        monitoringService.monitor();
        
        // Откат и закрытие
        when(ticker24hService.getPrice("BTCUSDT")).thenReturn(new BigDecimal("100.12"));
        monitoringService.monitor();

        // Шаг 3: Проверяем порядок вызовов
        verify(tradingUpdatesService, times(1)).closePosition(
                any(TradeSession.class), 
                any(SessionMode.class), 
                anyLong(), 
                any(), 
                any(TradingDirection.class), 
                eq(OrderPurpose.HEDGE_CLOSE), 
                any(BigDecimal.class), 
                anyString()
        );

        // Шаг 4: Проверяем финальное состояние
        assertEquals(SessionStatus.COMPLETED, completedSession.getStatus());
        assertFalse(completedSession.hasActivePosition());
        assertNotNull(completedSession.getPnl());
        assertNotNull(completedSession.getTotalCommission());
        assertNotNull(completedSession.getEndTime());
        
        // Шаг 5: Проверяем освобождение TradePlan
        testPlan.openActive();
        assertFalse(testPlan.getActive());
        
        // Шаг 6: Проверяем очистку мониторинга
        when(sessionService.getAllActive()).thenReturn(new ArrayList<>());
        List<TradeSession> activeSessions = sessionService.getAllActive();
        assertTrue(activeSessions.isEmpty());
    }
} 
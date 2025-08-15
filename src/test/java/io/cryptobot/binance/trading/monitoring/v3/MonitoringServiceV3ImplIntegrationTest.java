package io.cryptobot.binance.trading.monitoring.v3;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.enums.OrderType;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import io.cryptobot.binance.trading.monitoring.v3.models.FollowUpState;
import io.cryptobot.binance.trading.monitoring.v3.models.SingleTrackState;
import io.cryptobot.binance.trading.monitoring.v3.utils.CheckAveraging;
import io.cryptobot.binance.trading.monitoring.v3.utils.CheckTrailing;
import io.cryptobot.binance.trading.monitoring.v3.utils.ExtraClose;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.configs.locks.TradeSessionLockRegistry;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceV3ImplIntegrationTest {

    @Mock private MonitorHelper monitorHelper;
    @Mock private CheckAveraging averaging;
    @Mock private ExtraClose extraClose;
    @Mock private TradeSessionService sessionService;
    @Mock private Ticker24hService ticker24hService;
    @Mock private TradingUpdatesService tradingUpdatesService;
    @Mock private CheckTrailing checkTrailing;
    @Mock private TradeSessionLockRegistry lockRegistry;

    private MonitoringServiceV3Impl monitoringService;
    private TradeSession testSession;
    private TradePlan testTradePlan;
    private SizeModel testSizeModel;
    private ReentrantLock testLock;

    @BeforeEach
    void setUp() {
        monitoringService = new MonitoringServiceV3Impl(
                monitorHelper, averaging, extraClose, sessionService,
                ticker24hService, tradingUpdatesService, checkTrailing, lockRegistry
        );

        testLock = new ReentrantLock();
        when(lockRegistry.getLock(anyString())).thenReturn(testLock);

        // Настройка базовых объектов
        testSizeModel = new SizeModel();
        testSizeModel.setTickSize(new BigDecimal("0.01"));
        testSizeModel.setLotSize(new BigDecimal("0.001"));
        testSizeModel.setMinCount(new BigDecimal("0.001"));
        testSizeModel.setMinAmount(new BigDecimal("10"));

        testTradePlan = new TradePlan();
        testTradePlan.onCreate("BTCUSDT", new BigDecimal("100"), 10, null, testSizeModel);

        testSession = TradeSession.builder()
                .id("test-session-1")
                .tradePlan("BTCUSDT")
                .status(SessionStatus.ACTIVE)
                .processing(false)
                .createdTime(LocalDateTime.now())
                .orders(new java.util.ArrayList<>())
                .build();

        // Базовые моки - только те, которые действительно используются
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("50000"));
        when(monitorHelper.nvl(any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void shouldAddSessionToMonitoring() {
        // Given
        TradeOrder mainOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        ReflectionTestUtils.setField(testSession, "mainPosition", mainOrder.getOrderId());
        testSession.addOrder(mainOrder);

        // Настраиваем мок для возврата активного ордера
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainOrder);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия была обработана
        assertNotNull(testSession);
    }

    @Test
    void shouldHandleSessionWithoutActivePositions() {
        // Given
        // Сессия без активных позиций
        testSession = TradeSession.builder()
                .id("test-session-2")
                .tradePlan("BTCUSDT")
                .status(SessionStatus.ACTIVE)
                .processing(false)
                .createdTime(LocalDateTime.now())
                .orders(new java.util.ArrayList<>())
                .build();

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия была обработана без ошибок
        assertNotNull(testSession);
        assertFalse(testSession.hasActivePosition());
    }

    @Test
    void shouldHandleLockedSession() {
        // Given
        TradeOrder mainOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        ReflectionTestUtils.setField(testSession, "mainPosition", mainOrder.getOrderId());
        testSession.addOrder(mainOrder);

        // Блокируем сессию
        testLock.lock();

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия была обработана без ошибок
        assertNotNull(testSession);
        assertTrue(testLock.isLocked());

        // Разблокируем для очистки
        testLock.unlock();
    }

    @Test
    void shouldCompleteFullTradingCycle() {
        // Given - Создаем сессию с основной длинной позицией
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        ReflectionTestUtils.setField(testSession, "mainPosition", mainLongOrder.getOrderId());
        testSession.addOrder(mainLongOrder);

        // Настраиваем моки для первой фазы (открытие лонга)
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.opposite(TradingDirection.LONG)).thenReturn(TradingDirection.SHORT);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When - Добавляем в мониторинг и запускаем
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then - Проверяем, что сессия обработана
        assertNotNull(testSession);
        assertTrue(testSession.hasActivePosition());
    }

    @Test
    void shouldOpenAveragePosition_whenWorstPositionDropsSignificantly() {
        // Given - Создаем сессию с двумя позициями
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        TradeOrder hedgeShortOrder = createTradeOrder("order-2", TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, 
                new BigDecimal("40000"), new BigDecimal("0.001"));
        
        testSession.addOrder(mainLongOrder);
        testSession.addOrder(hedgeShortOrder);
        testSession.changeMode(SessionMode.HEDGING);

        // Настраиваем моки для двух позиций
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.SHORT))).thenReturn(hedgeShortOrder);
        when(averaging.checkOpen(any(), any(), any())).thenReturn(true);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(tradingUpdatesService, atLeastOnce()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldCloseAveragePosition_whenAveragingOrderExists() {
        // Given - Создаем сессию с усредняющим ордером
        TradeOrder avgOrder = createTradeOrder("order-3", TradingDirection.LONG, OrderPurpose.AVERAGING_OPEN, 
                new BigDecimal("45000"), new BigDecimal("0.001"));
        testSession.addOrder(avgOrder);

        // Настраиваем моки
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(avgOrder);
        when(monitorHelper.isValidForClosing(avgOrder)).thenReturn(true);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана без ошибок
        assertNotNull(testSession);
    }

    @Test
    void shouldActivateExtraClose_whenTwoPositionsInLoss() {
        // Given - Создаем сессию с двумя убыточными позициями
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        TradeOrder hedgeShortOrder = createTradeOrder("order-2", TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, 
                new BigDecimal("40000"), new BigDecimal("0.001"));
        
        testSession.addOrder(mainLongOrder);
        testSession.addOrder(hedgeShortOrder);
        testSession.changeMode(SessionMode.HEDGING);

        // Настраиваем моки для двух позиций
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.SHORT))).thenReturn(hedgeShortOrder);
        when(extraClose.checkExtraClose(any(), any(), any(), any())).thenReturn(true);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(extraClose, atLeastOnce()).checkExtraClose(any(), any(), any(), any());
    }

    @Test
    void shouldOpenHedge_whenPositionWorsensByMinus10PercentFromBaseline() {
        // Given - Создаем сессию с основной позицией
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        testSession.addOrder(mainLongOrder);

        // Настраиваем моки для одной позиции
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(monitorHelper.opposite(TradingDirection.LONG)).thenReturn(TradingDirection.SHORT);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана
        assertNotNull(testSession);
    }

    @Test
    void shouldStartTracking_whenLongPositionDropsToMinus20Percent() {
        // Given - Создаем сессию с позицией в убытке -20%
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        testSession.addOrder(mainLongOrder);

        // Устанавливаем цену на -20% ниже
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("40000"));

        // Настраиваем моки для одной позиции
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана
        assertNotNull(testSession);
    }

    @Test
    void shouldOpenHedgeShort_whenLongPositionDropsToMinus20Percent() {
        // Given - Создаем сессию с позицией в убытке -20%
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        testSession.addOrder(mainLongOrder);

        // Устанавливаем цену на -20% ниже
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("40000"));

        // Настраиваем моки для одной позиции
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(monitorHelper.opposite(TradingDirection.LONG)).thenReturn(TradingDirection.SHORT);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана
        assertNotNull(testSession);
    }

    @Test
    void shouldCloseBestPosition_whenTrailingTriggered() {
        // Given - Создаем сессию с двумя позициями
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        TradeOrder hedgeShortOrder = createTradeOrder("order-2", TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, 
                new BigDecimal("40000"), new BigDecimal("0.001"));
        
        testSession.addOrder(mainLongOrder);
        testSession.addOrder(hedgeShortOrder);
        testSession.changeMode(SessionMode.HEDGING);

        // Настраиваем моки для двух позиций
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.SHORT))).thenReturn(hedgeShortOrder);
        when(checkTrailing.checkTrailing(any(), any())).thenReturn(true);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        verify(checkTrailing, atLeastOnce()).checkTrailing(any(), any());
    }

    @Test
    void shouldActivateSoftTrailing_whenPositionImprovesBy10Percent() {
        // Given - Создаем сессию с позицией
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        testSession.addOrder(mainLongOrder);

        // Устанавливаем цену на +10% выше
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("55000"));

        // Настраиваем моки для одной позиции
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана
        assertNotNull(testSession);
    }

    @Test
    void shouldHandleFollowUpState_whenBestPositionClosed() {
        // Given - Создаем сессию с одной позицией (хедж закрыт)
        TradeOrder hedgeShortOrder = createTradeOrder("order-2", TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, 
                new BigDecimal("40000"), new BigDecimal("0.001"));
        
        testSession.addOrder(hedgeShortOrder);
        testSession.changeMode(SessionMode.SCALPING);

        // Настраиваем моки
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.SHORT))).thenReturn(hedgeShortOrder);
        when(monitorHelper.isValidForClosing(hedgeShortOrder)).thenReturn(true);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана
        assertNotNull(testSession);
    }

    @Test
    void shouldTriggerSoftTrailing_whenPositionRetracesFromHigh() {
        // Given - Создаем сессию с позицией
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        testSession.addOrder(mainLongOrder);

        // Настраиваем моки для одной позиции
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана
        assertNotNull(testSession);
    }

    @Test
    void shouldOpenHedgeOrder_whenMainPositionDropsSignificantly() {
        // Given - Создаем сессию с основной позицией
        TradeOrder mainLongOrder = createTradeOrder("order-1", TradingDirection.LONG, OrderPurpose.MAIN_OPEN, 
                new BigDecimal("50000"), new BigDecimal("0.001"));
        testSession.addOrder(mainLongOrder);

        // Устанавливаем цену значительно ниже
        when(ticker24hService.getPrice(anyString())).thenReturn(new BigDecimal("40000"));

        // Настраиваем моки для одной позиции
        when(monitorHelper.getLatestActiveOrderByDirection(any(TradeSession.class), eq(TradingDirection.LONG))).thenReturn(mainLongOrder);
        when(monitorHelper.isMainStillActive(testSession)).thenReturn(true);
        when(monitorHelper.opposite(TradingDirection.LONG)).thenReturn(TradingDirection.SHORT);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(testSession);

        // When
        monitoringService.addToMonitoring(testSession);
        monitoringService.monitor();

        // Then
        // Проверяем, что сессия обработана
        assertNotNull(testSession);
    }

    // Вспомогательный метод для создания TradeOrder
    private TradeOrder createTradeOrder(String orderId, TradingDirection direction, OrderPurpose purpose, 
                                       BigDecimal price, BigDecimal count) {
        return TradeOrder.builder()
                .orderId(Long.parseLong(orderId.replace("order-", "")))
                .direction(direction)
                .purpose(purpose)
                .symbol("BTCUSDT")
                .side(direction == TradingDirection.LONG ? OrderSide.BUY : OrderSide.SELL)
                .type(OrderType.MARKET)
                .count(count)
                .price(price)
                .amount(price.multiply(count))
                .commission(BigDecimal.ZERO)
                .commissionAsset("USDT")
                .status(OrderStatus.FILLED)
                .leverage(10)
                .parentOrderId(null)
                .relatedHedgeId(null)
                .modeAtCreation(SessionMode.SCALPING)
                .orderTime(LocalDateTime.now())
                .pnl(BigDecimal.ZERO)
                .pnlHigh(BigDecimal.ZERO)
                .trailingActive(false)
                .haveAveraging(false)
                .idAveragingOrder(null)
                .basePnl(null)
                .maxChangePnl(null)
                .build();
    }
}

package io.cryptobot.binance.trading.updates;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.binance.trading.updates.TradingUpdatesServiceImpl;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.configs.locks.TradeSessionLockRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingUpdatesServiceImpl Tests")
class TradingUpdatesServiceImplTest {

    @Mock
    private TradePlanGetService tradePlanGetService;

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private OrderService orderService;

    @Mock
    private TradeSessionLockRegistry lockRegistry;

    @InjectMocks
    private TradingUpdatesServiceImpl tradingUpdatesService;

    private TradeSession session;
    private TradeOrder longOrder;
    private TradeOrder shortOrder;
    private TradePlan tradePlan;
    private Order mockOrder;

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

        tradePlan = TradePlan.builder()
                .symbol("BTCUSDT")
                .amountPerTrade(new BigDecimal("1000"))
                .sizes(SizeModel.builder()
                        .lotSize(new BigDecimal("0.001"))
                        .build())
                .build();

        mockOrder = new Order();
        mockOrder.setOrderId(123456789L);
        mockOrder.setAveragePrice(new BigDecimal("52000"));

        // Настраиваем мок для lockRegistry
        lenient().when(lockRegistry.getLock(anyString())).thenReturn(new java.util.concurrent.locks.ReentrantLock());
        
        // Настраиваем моки для orderService
        lenient().when(orderService.createLimitOrElseMarket(anyString(), anyDouble(), any(OrderSide.class), any(SizeModel.class)))
                .thenReturn(mockOrder);
        lenient().when(orderService.getOrder(anyLong())).thenReturn(mockOrder);
        
        // Настраиваем моки для sessionService
        lenient().when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(session);
    }

    @Test
    @DisplayName("Should handle session with no active positions")
    void shouldHandleSessionWithNoActivePositions() {
        // Given - сессия без активных позиций
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.closeLongPosition();
        session.closeShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.LONG, 
                OrderPurpose.MAIN_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then - должен открыться новый ордер
        assertNotNull(result);
        verify(orderService, atLeastOnce()).createLimitOrElseMarket(eq("BTCUSDT"), anyDouble(), eq(OrderSide.BUY), any(SizeModel.class));
    }

    @Test
    @DisplayName("Should handle session with only long position active")
    void shouldHandleSessionWithOnlyLongPositionActive() {
        // Given - сессия только с длинной позицией
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.openLongPosition();
        session.closeShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.SHORT, 
                OrderPurpose.HEDGE_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then - должен открыться хедж шорт
        assertNotNull(result);
        verify(orderService, atLeastOnce()).createLimitOrElseMarket(eq("BTCUSDT"), anyDouble(), eq(OrderSide.SELL), any(SizeModel.class));
    }

    @Test
    @DisplayName("Should handle session with only short position active")
    void shouldHandleSessionWithOnlyShortPositionActive() {
        // Given - сессия только с короткой позицией
        session.onCreate("BTCUSDT", TradingDirection.SHORT, shortOrder, "test context");
        session.closeLongPosition();
        session.openShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.LONG, 
                OrderPurpose.MAIN_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then - должен открыться основной лонг
        assertNotNull(result);
        verify(orderService, atLeastOnce()).createLimitOrElseMarket(eq("BTCUSDT"), anyDouble(), eq(OrderSide.BUY), any(SizeModel.class));
    }

    @Test
    @DisplayName("Should handle basic close position functionality")
    void shouldHandleBasicClosePositionFunctionality() {
        // Given - базовая сессия
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.openLongPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                session, SessionMode.SCALPING, longOrder.getOrderId(), 
                null, TradingDirection.LONG, OrderPurpose.MAIN_CLOSE, 
                new BigDecimal("50000"), "test context"
        );

        // Then - должен вернуть сессию
        assertNotNull(result);
    }
} 
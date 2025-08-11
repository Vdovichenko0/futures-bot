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
        // TradeSession не имеет setTradePlan, используем onCreate

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
    }

    @Test
    @DisplayName("Should close position when both LONG and SHORT positions are active")
    void testClosePositionWhenBothPositionsActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.addOrder(shortOrder);
        session.openLongPosition();
        session.openShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);
        when(orderService.closeOrder(longOrder)).thenReturn(mockOrder);
        when(orderService.getOrder(123456789L)).thenReturn(mockOrder);
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(session);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                session,
                SessionMode.HEDGING,
                1001L,
                null,
                TradingDirection.LONG,
                OrderPurpose.MAIN_CLOSE,
                new BigDecimal("52000"),
                "test context"
        );

        // Then
        assertNotNull(result);
        verify(orderService).closeOrder(longOrder);
        verify(orderService).getOrder(123456789L);
        verify(sessionService).addOrder(eq("test-session"), any(TradeOrder.class));
    }

    @Test
    @DisplayName("Should proceed with close when only LONG position is active")
    void testClosePositionWhenOnlyLongActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.openLongPosition();
        session.closeShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);
        when(orderService.closeOrder(longOrder)).thenReturn(mockOrder);
        when(orderService.getOrder(123456789L)).thenReturn(mockOrder);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                session,
                SessionMode.SCALPING,
                1001L,
                null,
                TradingDirection.LONG,
                OrderPurpose.MAIN_CLOSE,
                new BigDecimal("52000"),
                "test context"
        );

        // Then
        assertNotNull(result);
        verify(orderService).closeOrder(longOrder);
        verify(orderService).getOrder(123456789L);
    }

    @Test
    @DisplayName("Should proceed with close when only SHORT position is active")
    void testClosePositionWhenOnlyShortActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.SHORT, shortOrder, "test context");
        session.closeLongPosition();
        session.openShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);
        when(orderService.closeOrder(shortOrder)).thenReturn(mockOrder);
        when(orderService.getOrder(123456789L)).thenReturn(mockOrder);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                session,
                SessionMode.HEDGING,
                1002L,
                null,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_CLOSE,
                new BigDecimal("52000"),
                "test context"
        );

        // Then
        assertNotNull(result);
        verify(orderService).closeOrder(shortOrder);
        verify(orderService).getOrder(123456789L);
    }

    @Test
    @DisplayName("Should proceed with close when no positions are active")
    void testClosePositionWhenNoPositionsActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.closeLongPosition();
        session.closeShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);
        when(orderService.closeOrder(longOrder)).thenReturn(mockOrder);
        when(orderService.getOrder(123456789L)).thenReturn(mockOrder);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                session,
                SessionMode.SCALPING,
                1001L,
                null,
                TradingDirection.LONG,
                OrderPurpose.MAIN_CLOSE,
                new BigDecimal("52000"),
                "test context"
        );

        // Then
        assertNotNull(result);
        verify(orderService).closeOrder(longOrder);
        verify(orderService).getOrder(123456789L);
    }

    @Test
    @DisplayName("Should not open new position when both LONG and SHORT positions are active")
    void testOpenPositionWhenBothPositionsActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.addOrder(shortOrder);
        session.openLongPosition();
        session.openShortPosition();

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.LONG, 
                OrderPurpose.MAIN_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then
        assertSame(session, result);
        verifyNoInteractions(orderService);
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("Should not open new LONG position when LONG position is already active")
    void testOpenPositionWhenLongAlreadyActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.openLongPosition();
        session.closeShortPosition();

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.LONG, 
                OrderPurpose.MAIN_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then
        assertSame(session, result);
        verifyNoInteractions(orderService);
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("Should not open new SHORT position when SHORT position is already active")
    void testOpenPositionWhenShortAlreadyActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.SHORT, shortOrder, "test context");
        session.closeLongPosition();
        session.openShortPosition();

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.SHORT, 
                OrderPurpose.HEDGE_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then
        assertSame(session, result);
        verifyNoInteractions(orderService);
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("Should allow opening SHORT position when only LONG position is active")
    void testOpenPositionWhenOnlyLongActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.LONG, longOrder, "test context");
        session.openLongPosition();
        session.closeShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);
        when(orderService.createOrder(anyString(), anyDouble(), any(), anyBoolean()))
                .thenReturn(mockOrder);
        when(orderService.getOrder(123456789L)).thenReturn(mockOrder);
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(session);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.SHORT, 
                OrderPurpose.HEDGE_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then
        assertNotNull(result);
        verify(orderService).createOrder(eq("BTCUSDT"), anyDouble(), eq(OrderSide.SELL), eq(true));
        verify(sessionService).addOrder(eq("test-session"), any(TradeOrder.class));
    }

    @Test
    @DisplayName("Should allow opening LONG position when only SHORT position is active")
    void testOpenPositionWhenOnlyShortActive() {
        // Given
        session.onCreate("BTCUSDT", TradingDirection.SHORT, shortOrder, "test context");
        session.closeLongPosition();
        session.openShortPosition();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(tradePlan);
        when(orderService.createOrder(anyString(), anyDouble(), any(), anyBoolean()))
                .thenReturn(mockOrder);
        when(orderService.getOrder(123456789L)).thenReturn(mockOrder);
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(session);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                session, SessionMode.SCALPING, TradingDirection.LONG, 
                OrderPurpose.MAIN_OPEN, new BigDecimal("50000"), "test context", 
                null, null
        );

        // Then
        assertNotNull(result);
        verify(orderService).createOrder(eq("BTCUSDT"), anyDouble(), eq(OrderSide.BUY), eq(true));
        verify(sessionService).addOrder(eq("test-session"), any(TradeOrder.class));
    }
} 
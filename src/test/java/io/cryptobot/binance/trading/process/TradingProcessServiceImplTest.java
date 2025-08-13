package io.cryptobot.binance.trading.process;

import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trading.monitoring.v3.MonitoringServiceV3;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingProcessServiceImpl Tests")
class TradingProcessServiceImplTest {

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private OrderService orderService;

    @Mock
    private MonitoringServiceV3 monitoringService;

    @InjectMocks
    private TradingProcessServiceImpl tradingProcessService;

    private TradePlan testTradePlan;
    private Order testOrder;
    private TradeSession testTradeSession;
    private SizeModel testSizeModel;

    @BeforeEach
    void setUp() {
        // Подготовка SizeModel
        testSizeModel = SizeModel.builder()
                .tickSize(new BigDecimal("0.01"))
                .lotSize(new BigDecimal("0.001"))
                .minCount(new BigDecimal("0.001"))
                .minAmount(new BigDecimal("10.00"))
                .build();

        // Подготовка TradePlan
        testTradePlan = new TradePlan();
        testTradePlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, null, testSizeModel);

        // Подготовка Order
        testOrder = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.002"))
                .averagePrice(new BigDecimal("50000.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        // Подготовка TradeSession
        testTradeSession = TradeSession.builder()
                .id("session-123")
                .direction(TradingDirection.LONG)
                .build();
        
        // Устанавливаем короткие значения для тестов
        tradingProcessService.setMaxWaitMillis(100);
        tradingProcessService.setIntervalMillis(50);
    }

    @Test
    @DisplayName("Should open order successfully for LONG direction")
    void testOpenOrderSuccessfullyForLong() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(testOrder);
        when(sessionService.create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context)))
                .thenReturn(testTradeSession);

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService).create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context));
        verify(monitoringService).addToMonitoring(testTradeSession);
    }

    @Test
    @DisplayName("Should open order successfully for SHORT direction")
    void testOpenOrderSuccessfullyForShort() {
        // Given
        TradingDirection direction = TradingDirection.SHORT;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.SELL), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(testOrder);
        when(sessionService.create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context)))
                .thenReturn(testTradeSession);

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.SELL), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService).create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context));
        verify(monitoringService).addToMonitoring(testTradeSession);
    }

    @Test
    @DisplayName("Should throw exception when quantity is zero or negative")
    void testOpenOrderWithInvalidQuantity() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        // Create a trade plan with very small amount
        TradePlan smallAmountPlan = new TradePlan();
        smallAmountPlan.onCreate("BTCUSDT", new BigDecimal("0.0000000000000001"), 10, null, testSizeModel);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> tradingProcessService.openOrder(smallAmountPlan, direction, currentPrice, context));

        assertTrue(exception.getMessage().contains("Invalid quantity"));

        // Verify no interactions
        verify(orderService, never()).createOrder(anyString(), anyDouble(), any(OrderSide.class), anyBoolean());
        verify(sessionService, never()).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle order not filled in time")
    void testOpenOrderWhenOrderNotFilled() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        Order pendingOrder = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.002"))
                .orderStatus(OrderStatus.NEW) // Not filled
                .build();

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenReturn(pendingOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(pendingOrder); // Always return pending order

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService, never()).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle null order from createOrder")
    void testOpenOrderWhenCreateOrderReturnsNull() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenReturn(null);

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder("BTCUSDT", 0.002, OrderSide.BUY, true);
        verify(orderService, never()).getOrder(anyLong());
        verify(sessionService, never()).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle exception during order creation")
    void testOpenOrderWhenOrderCreationThrowsException() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenThrow(new RuntimeException("API error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context));

        // Verify interactions
        verify(orderService).createOrder("BTCUSDT", 0.002, OrderSide.BUY, true);
        verify(orderService, never()).getOrder(anyLong());
        verify(sessionService, never()).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle exception during session creation")
    void testOpenOrderWhenSessionCreationThrowsException() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(testOrder);
        when(sessionService.create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context)))
                .thenThrow(new RuntimeException("Session creation error"));

        // When & Then
        assertThrows(RuntimeException.class,
                () -> tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context));

        // Verify interactions
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService).create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context));
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle exception during monitoring service call")
    void testOpenOrderWhenMonitoringServiceThrowsException() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(testOrder);
        when(sessionService.create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context)))
                .thenReturn(testTradeSession);
        doThrow(new RuntimeException("Monitoring error")).when(monitoringService).addToMonitoring(testTradeSession);

        // When & Then
        assertThrows(RuntimeException.class,
                () -> tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context));

        // Verify interactions
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService).create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context));
        verify(monitoringService).addToMonitoring(testTradeSession);
    }

    @Test
    @DisplayName("Should calculate quantity correctly with different lot sizes")
    void testOpenOrderWithDifferentLotSizes() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        // Different lot size
        SizeModel customSizeModel = SizeModel.builder()
                .tickSize(new BigDecimal("0.01"))
                .lotSize(new BigDecimal("0.01")) // Different lot size
                .minCount(new BigDecimal("0.001"))
                .minAmount(new BigDecimal("10.00"))
                .build();

        TradePlan customTradePlan = new TradePlan();
        customTradePlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, null, customSizeModel);

        when(orderService.createOrder(anyString(), anyDouble(), any(OrderSide.class), anyBoolean()))
                .thenReturn(testOrder);
        when(orderService.getOrder(anyLong()))
                .thenReturn(testOrder);
        when(sessionService.create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString()))
                .thenReturn(testTradeSession);

        // When
        tradingProcessService.openOrder(customTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder(anyString(), anyDouble(), any(OrderSide.class), anyBoolean());
        verify(orderService, atLeastOnce()).getOrder(anyLong());
        verify(sessionService).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService).addToMonitoring(testTradeSession);
    }

    @Test
    @DisplayName("Should handle null trade plan")
    void testOpenOrderWithNullTradePlan() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        // When & Then
        assertThrows(NullPointerException.class,
                () -> tradingProcessService.openOrder(null, direction, currentPrice, context));

        // Verify no interactions
        verify(orderService, never()).createOrder(anyString(), anyDouble(), any(OrderSide.class), anyBoolean());
        verify(sessionService, never()).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle null current price")
    void testOpenOrderWithNullCurrentPrice() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = null;
        String context = "test-context";

        // When & Then
        assertThrows(NullPointerException.class,
                () -> tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context));

        // Verify no interactions
        verify(orderService, never()).createOrder(anyString(), anyDouble(), any(OrderSide.class), anyBoolean());
        verify(sessionService, never()).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle zero current price")
    void testOpenOrderWithZeroCurrentPrice() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = BigDecimal.ZERO;
        String context = "test-context";

        // When & Then
        assertThrows(ArithmeticException.class,
                () -> tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context));

        // Verify no interactions
        verify(orderService, never()).createOrder(anyString(), anyDouble(), any(OrderSide.class), anyBoolean());
        verify(sessionService, never()).create(anyString(), any(TradingDirection.class), any(TradeOrder.class), anyString());
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should handle null context")
    void testOpenOrderWithNullContext() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = null;

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(testOrder);
        when(sessionService.create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(null)))
                .thenReturn(testTradeSession);

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService).create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(null));
        verify(monitoringService).addToMonitoring(testTradeSession);
    }

    @Test
    @DisplayName("Should handle different amounts per trade")
    void testOpenOrderWithDifferentAmounts() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        // Different amount per trade
        TradePlan customTradePlan = new TradePlan();
        customTradePlan.onCreate("BTCUSDT", new BigDecimal("200.00"), 10, null, testSizeModel);

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.004), eq(OrderSide.BUY), eq(true))) // 200/50000 = 0.004
                .thenReturn(testOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(testOrder);
        when(sessionService.create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context)))
                .thenReturn(testTradeSession);

        // When
        tradingProcessService.openOrder(customTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.004), eq(OrderSide.BUY), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService).create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context));
        verify(monitoringService).addToMonitoring(testTradeSession);
    }

    @Test
    @DisplayName("Should set and get max wait milliseconds")
    void testSetAndGetMaxWaitMillis() {
        // Given
        int newMaxWait = 10000;

        // When
        tradingProcessService.setMaxWaitMillis(newMaxWait);

        // Then
        assertEquals(newMaxWait, tradingProcessService.getMaxWaitMillis());
    }

    @Test
    @DisplayName("Should set and get interval milliseconds")
    void testSetAndGetIntervalMillis() {
        // Given
        int newInterval = 1000;

        // When
        tradingProcessService.setIntervalMillis(newInterval);

        // Then
        assertEquals(newInterval, tradingProcessService.getIntervalMillis());
    }

    @Test
    @DisplayName("Should use custom wait limits when opening order")
    void testOpenOrderWithCustomWaitLimits() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        // Set custom limits
        tradingProcessService.setMaxWaitMillis(3000);
        tradingProcessService.setIntervalMillis(300);

        when(orderService.createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(eq(123456789L)))
                .thenReturn(testOrder);
        when(sessionService.create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context)))
                .thenReturn(testTradeSession);

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createOrder(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(true));
        verify(orderService, atLeastOnce()).getOrder(eq(123456789L));
        verify(sessionService).create(eq("BTCUSDT"), eq(direction), any(TradeOrder.class), eq(context));
        verify(monitoringService).addToMonitoring(testTradeSession);
    }

    @Test
    @DisplayName("Should handle order filled immediately")
    void testWaitForFilledOrderImmediateFill() {
        // Given
        Order filledOrder = Order.builder()
                .orderId(123456789L)
                .orderStatus(OrderStatus.FILLED)
                .build();

        when(orderService.getOrder(123456789L))
                .thenReturn(filledOrder);

        // When
        boolean result = tradingProcessService.waitForFilledOrder(filledOrder, 5000, 500);

        // Then
        assertTrue(result);
        verify(orderService, atLeastOnce()).getOrder(123456789L);
    }

    @Test
    @DisplayName("Should handle order filled after delay")
    void testWaitForFilledOrderDelayedFill() {
        // Given
        Order pendingOrder = Order.builder()
                .orderId(123456789L)
                .orderStatus(OrderStatus.NEW)
                .build();

        Order filledOrder = Order.builder()
                .orderId(123456789L)
                .orderStatus(OrderStatus.FILLED)
                .build();

        when(orderService.getOrder(123456789L))
                .thenReturn(pendingOrder) // First call returns pending
                .thenReturn(filledOrder); // Second call returns filled

        // When
        boolean result = tradingProcessService.waitForFilledOrder(pendingOrder, 2000, 100);

        // Then
        assertTrue(result);
        verify(orderService, times(2)).getOrder(123456789L);
    }

    @Test
    @DisplayName("Should return false when order not filled in time")
    void testWaitForFilledOrderTimeout() {
        // Given
        Order pendingOrder = Order.builder()
                .orderId(123456789L)
                .orderStatus(OrderStatus.NEW)
                .build();

        when(orderService.getOrder(123456789L))
                .thenReturn(pendingOrder); // Always return pending

        // When
        boolean result = tradingProcessService.waitForFilledOrder(pendingOrder, 100, 50);

        // Then
        assertFalse(result);
        verify(orderService, atLeastOnce()).getOrder(123456789L);
    }

    @Test
    @DisplayName("Should handle interrupted thread")
    void testWaitForFilledOrderInterrupted() {
        // Given
        Order pendingOrder = Order.builder()
                .orderId(123456789L)
                .orderStatus(OrderStatus.NEW)
                .build();

        when(orderService.getOrder(123456789L))
                .thenReturn(pendingOrder);

        // When
        Thread.currentThread().interrupt();
        boolean result = tradingProcessService.waitForFilledOrder(pendingOrder, 5000, 500);

        // Then
        assertFalse(result);
        verify(orderService, atLeastOnce()).getOrder(123456789L);
        
        // Clear interrupt flag for other tests
        Thread.interrupted();
    }

    @Test
    @DisplayName("Should handle null order in waitForFilledOrder")
    void testWaitForFilledOrderWithNullOrder() {
        // When
        boolean result = tradingProcessService.waitForFilledOrder(null, 5000, 500);
        
        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should handle negative max wait milliseconds")
    void testWaitForFilledOrderWithNegativeMaxWait() {
        // Given
        Order testOrder = Order.builder()
                .orderId(123456789L)
                .orderStatus(OrderStatus.NEW)
                .build();

        // When
        boolean result = tradingProcessService.waitForFilledOrder(testOrder, -1000, 500);

        // Then
        assertFalse(result);
        verify(orderService, never()).getOrder(anyLong());
    }

    @Test
    @DisplayName("Should handle zero interval milliseconds")
    void testWaitForFilledOrderWithZeroInterval() {
        // Given
        Order testOrder = Order.builder()
                .orderId(123456789L)
                .orderStatus(OrderStatus.NEW)
                .build();

        when(orderService.getOrder(123456789L))
                .thenReturn(testOrder);

        // When
        boolean result = tradingProcessService.waitForFilledOrder(testOrder, 100, 0);

        // Then
        assertFalse(result);
        verify(orderService, atLeastOnce()).getOrder(123456789L);
    }
} 
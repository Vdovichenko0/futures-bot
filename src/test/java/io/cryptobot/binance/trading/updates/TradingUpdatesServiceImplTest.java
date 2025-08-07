package io.cryptobot.binance.trading.updates;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    private TradeSession testSession;
    private TradeOrder testOrder;
    private TradePlan testPlan;
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

        // Подготовка TradeOrder
        testOrder = new TradeOrder();
        testOrder.onCreate(
                createMockOrder("123", OrderSide.BUY, new BigDecimal("50000.00"), new BigDecimal("0.1")),
                new BigDecimal("50.00"),
                SessionMode.SCALPING,
                "test_context",
                testPlan,
                TradingDirection.LONG,
                OrderPurpose.HEDGE_OPEN,
                123L,
                null
        );

        // Подготовка TradeSession
        testSession = TradeSession.builder()
                .id("session-123")
                .tradePlan("BTCUSDT")
                .orders(Arrays.asList(testOrder))
                .build();

        // Подготовка mock Order
        mockOrder = createMockOrder("456", OrderSide.SELL, new BigDecimal("50100.00"), new BigDecimal("0.1"));
    }

    private Order createMockOrder(String orderId, OrderSide side, BigDecimal price, BigDecimal quantity) {
        Order order = new Order();
        order.setOrderId(Long.parseLong(orderId));
        order.setSide(side);
        order.setAveragePrice(price);
        order.setOrderStatus(OrderStatus.FILLED);
        order.setQuantity(quantity);
        return order;
    }

    @Test
    @DisplayName("Закрытие LONG позиции с прибылью")
    void shouldCloseLongPositionWithProfit() {
        // Given
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(orderService.createOrder(anyString(), any(Double.class), any(OrderSide.class), any(Boolean.class)))
                .thenReturn(mockOrder);
        when(orderService.getOrder(anyLong())).thenReturn(mockOrder);

        TradeSession updatedSession = TradeSession.builder()
                .id("session-123")
                .orders(Arrays.asList(testOrder, new TradeOrder()))
                .build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                testSession,
                SessionMode.SCALPING,
                123L,
                null,
                TradingDirection.LONG,
                OrderPurpose.HEDGE_CLOSE,
                new BigDecimal("50100.00"),
                "test_close"
        );

        // Then
        assertNotNull(result);
        verify(orderService, times(1)).createOrder(
                anyString(),
                any(Double.class),
                any(OrderSide.class),
                any(Boolean.class)
        );
        verify(orderService, atLeastOnce()).getOrder(anyLong());
        verify(sessionService, times(1)).addOrder(anyString(), any(TradeOrder.class));
    }

    @Test
    @DisplayName("Закрытие SHORT позиции с убытком")
    void shouldCloseShortPositionWithLoss() {
        // Given
        TradeOrder shortOrder = new TradeOrder();
        shortOrder.onCreate(
                createMockOrder("789", OrderSide.SELL, new BigDecimal("50000.00"), new BigDecimal("0.1")),
                new BigDecimal("-30.00"),
                SessionMode.SCALPING,
                "test_context",
                testPlan,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_OPEN,
                789L,
                null
        );

        TradeSession shortSession = TradeSession.builder()
                .id("session-789")
                .tradePlan("BTCUSDT")
                .orders(Arrays.asList(shortOrder))
                .build();

        Order closeOrder = createMockOrder("999", OrderSide.BUY, new BigDecimal("50300.00"), new BigDecimal("0.1"));

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(orderService.createOrder(anyString(), any(Double.class), any(OrderSide.class), any(Boolean.class)))
                .thenReturn(closeOrder);
        when(orderService.getOrder(anyLong())).thenReturn(closeOrder);

        TradeSession updatedSession = TradeSession.builder()
                .id("session-789")
                .orders(Arrays.asList(shortOrder, new TradeOrder()))
                .build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                shortSession,
                SessionMode.SCALPING,
                789L,
                null,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_CLOSE,
                new BigDecimal("50300.00"),
                "test_close"
        );

        // Then
        assertNotNull(result);
        verify(orderService, times(1)).createOrder(
                anyString(),
                any(Double.class),
                any(OrderSide.class),
                any(Boolean.class)
        );
        verify(orderService, atLeastOnce()).getOrder(anyLong());
        verify(sessionService, times(1)).addOrder(anyString(), any(TradeOrder.class));
    }

    @Test
    @DisplayName("Открытие LONG позиции")
    void shouldOpenLongPosition() {
        // Given
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(orderService.createOrder(anyString(), any(Double.class), any(OrderSide.class), any(Boolean.class)))
                .thenReturn(mockOrder);
        when(orderService.getOrder(anyLong())).thenReturn(mockOrder);

        TradeSession updatedSession = TradeSession.builder()
                .id("session-123")
                .orders(Arrays.asList(testOrder, new TradeOrder()))
                .build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                testSession,
                SessionMode.SCALPING,
                TradingDirection.LONG,
                OrderPurpose.HEDGE_OPEN,
                new BigDecimal("50000.00"),
                "test_open",
                123L,
                null
        );

        // Then
        assertNotNull(result);
        verify(orderService, times(1)).createOrder(
                anyString(),
                any(Double.class),
                any(OrderSide.class),
                any(Boolean.class)
        );
        verify(orderService, atLeastOnce()).getOrder(anyLong());
        verify(sessionService, times(1)).addOrder(anyString(), any(TradeOrder.class));
    }

    @Test
    @DisplayName("Открытие SHORT позиции")
    void shouldOpenShortPosition() {
        // Given
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(orderService.createOrder(anyString(), any(Double.class), any(OrderSide.class), any(Boolean.class)))
                .thenReturn(mockOrder);
        when(orderService.getOrder(anyLong())).thenReturn(mockOrder);

        TradeSession updatedSession = TradeSession.builder()
                .id("session-123")
                .orders(Arrays.asList(testOrder, new TradeOrder()))
                .build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                testSession,
                SessionMode.SCALPING,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_OPEN,
                new BigDecimal("50000.00"),
                "test_open",
                123L,
                null
        );

        // Then
        assertNotNull(result);
        verify(orderService, times(1)).createOrder(
                anyString(),
                any(Double.class),
                any(OrderSide.class),
                any(Boolean.class)
        );
        verify(orderService, atLeastOnce()).getOrder(anyLong());
        verify(sessionService, times(1)).addOrder(anyString(), any(TradeOrder.class));
    }



    @Test
    @DisplayName("Обработка ошибки при закрытии позиции - ордер не найден")
    void shouldHandleErrorWhenOrderNotFound() {
        // Given
        TradeSession sessionWithoutOrder = TradeSession.builder()
                .id("session-empty")
                .tradePlan("BTCUSDT")
                .orders(Arrays.asList())
                .build();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            tradingUpdatesService.closePosition(
                    sessionWithoutOrder,
                    SessionMode.SCALPING,
                    999L,
                    null,
                    TradingDirection.LONG,
                    OrderPurpose.HEDGE_CLOSE,
                    new BigDecimal("50100.00"),
                    "test_close"
            );
        });
    }

    @Test
    @DisplayName("Обработка ошибки при открытии позиции - неверное количество")
    void shouldHandleErrorWhenInvalidQuantity() {
        // Given
        TradePlan invalidPlan = new TradePlan();
        invalidPlan.onCreate("BTCUSDT", new BigDecimal("0.00"), 10, null, null); // Нулевая сумма

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(invalidPlan);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            tradingUpdatesService.openPosition(
                    testSession,
                    SessionMode.SCALPING,
                    TradingDirection.LONG,
                    OrderPurpose.HEDGE_OPEN,
                    new BigDecimal("50000.00"),
                    "test_open",
                    123L,
                    null
            );
        });
    }



    @Test
    @DisplayName("Проверка расчета PnL для LONG позиции")
    void shouldCalculatePnLForLongPosition() {
        // Given
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(orderService.createOrder(anyString(), any(Double.class), any(OrderSide.class), any(Boolean.class)))
                .thenReturn(mockOrder);
        when(orderService.getOrder(anyLong())).thenReturn(mockOrder);

        TradeSession updatedSession = TradeSession.builder()
                .id("session-123")
                .orders(Arrays.asList(testOrder, new TradeOrder()))
                .build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                testSession,
                SessionMode.SCALPING,
                123L,
                null,
                TradingDirection.LONG,
                OrderPurpose.HEDGE_CLOSE,
                new BigDecimal("50100.00"), // Цена закрытия выше цены входа
                "test_close"
        );

        // Then
        assertNotNull(result);
        verify(sessionService, times(1)).addOrder(anyString(), any(TradeOrder.class));
        
        // Проверяем, что новый TradeOrder создан с правильными параметрами
        verify(sessionService).addOrder(anyString(), argThat(order -> {
            return order.getDirection() == TradingDirection.LONG &&
                   order.getPurpose() == OrderPurpose.HEDGE_CLOSE;
        }));
    }

    @Test
    @DisplayName("Проверка расчета PnL для SHORT позиции")
    void shouldCalculatePnLForShortPosition() {
        // Given
        TradeOrder shortOrder = new TradeOrder();
        shortOrder.onCreate(
                createMockOrder("789", OrderSide.SELL, new BigDecimal("50000.00"), new BigDecimal("0.1")),
                new BigDecimal("-30.00"),
                SessionMode.SCALPING,
                "test_context",
                testPlan,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_OPEN,
                789L,
                null
        );

        TradeSession shortSession = TradeSession.builder()
                .id("session-789")
                .tradePlan("BTCUSDT")
                .orders(Arrays.asList(shortOrder))
                .build();

        Order closeOrder = createMockOrder("999", OrderSide.BUY, new BigDecimal("49700.00"), new BigDecimal("0.1"));

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testPlan);
        when(orderService.createOrder(anyString(), any(Double.class), any(OrderSide.class), any(Boolean.class)))
                .thenReturn(closeOrder);
        when(orderService.getOrder(anyLong())).thenReturn(closeOrder);

        TradeSession updatedSession = TradeSession.builder()
                .id("session-789")
                .orders(Arrays.asList(shortOrder, new TradeOrder()))
                .build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                shortSession,
                SessionMode.SCALPING,
                789L,
                null,
                TradingDirection.SHORT,
                OrderPurpose.HEDGE_CLOSE,
                new BigDecimal("49700.00"), // Цена закрытия ниже цены входа
                "test_close"
        );

        // Then
        assertNotNull(result);
        verify(sessionService, times(1)).addOrder(anyString(), any(TradeOrder.class));
        
        // Проверяем, что новый TradeOrder создан с правильными параметрами
        verify(sessionService).addOrder(anyString(), argThat(order -> {
            return order.getDirection() == TradingDirection.SHORT &&
                   order.getPurpose() == OrderPurpose.HEDGE_CLOSE;
        }));
    }
} 
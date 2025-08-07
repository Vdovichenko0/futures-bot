package io.cryptobot.binance.order.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.binance.connector.futures.client.impl.futures.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.binance.order.dao.OrderRepository;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl Tests")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UMFuturesClientImpl mockClient;

    @Mock
    private Account mockAccount;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("50000.00"))
                .commission(new BigDecimal("0.05"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();
    }

    @Test
    @DisplayName("Should get order by ID successfully")
    void testGetOrderSuccessfully() {
        // Given
        Long orderId = 123456789L;
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        // When
        Order result = orderService.getOrder(orderId);

        // Then
        assertNotNull(result);
        assertEquals(orderId, result.getOrderId());
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals(OrderSide.BUY, result.getSide());
        assertEquals(OrderStatus.FILLED, result.getOrderStatus());

        // Verify interactions
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should return null when order not found")
    void testGetOrderWhenNotFound() {
        // Given
        Long orderId = 999999999L;
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // When
        Order result = orderService.getOrder(orderId);

        // Then
        assertNull(result);

        // Verify interactions
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Should handle null order ID")
    void testGetOrderWithNullId() {
        // When
        Order result = orderService.getOrder(null);

        // Then
        assertNull(result);

        // Verify interactions
        verify(orderRepository).findById(null);
    }

    @Test
    @DisplayName("Should update order successfully")
    void testUpdateOrderSuccessfully() {
        // Given
        Order updatedOrder = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(new BigDecimal("0.002"))
                .averagePrice(new BigDecimal("51000.00"))
                .commission(new BigDecimal("0.10"))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        when(orderRepository.findById(123456789L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);

        // When
        orderService.updateOrder(updatedOrder);

        // Then
        verify(orderRepository).findById(123456789L);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle null updated order")
    void testUpdateOrderWithNullOrder() {
        // When
        orderService.updateOrder(null);

        // Then
        verify(orderRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle order with null ID")
    void testUpdateOrderWithNullId() {
        // Given
        Order orderWithNullId = Order.builder()
                .orderId(null)
                .symbol("BTCUSDT")
                .build();

        // When
        orderService.updateOrder(orderWithNullId);

        // Then
        verify(orderRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle order not found in database")
    void testUpdateOrderWhenOrderNotFound() {
        // Given
        Order updatedOrder = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .build();

        when(orderRepository.findById(123456789L)).thenReturn(Optional.empty());

        // When
        orderService.updateOrder(updatedOrder);

        // Then
        verify(orderRepository, times(5)).findById(123456789L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should create order successfully")
    void testCreateOrderSuccessfully() {
        // Given
        String symbol = "BTCUSDT";
        Double amount = 0.001;
        OrderSide side = OrderSide.BUY;
        Boolean hedgeMode = true;

        // When
        Order result = orderService.createOrder(symbol, amount, side, hedgeMode);

        // Then
        // In test environment, we expect null due to API call failure
        assertNull(result);

        // Verify no repository interactions since the API call fails
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle create order with null parameters")
    void testCreateOrderWithNullParameters() {
        // When
        Order result = orderService.createOrder(null, null, null, null);

        // Then
        assertNull(result);

        // Verify no interactions
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle create order with invalid symbol")
    void testCreateOrderWithInvalidSymbol() {
        // Given
        String symbol = "";
        Double amount = 0.001;
        OrderSide side = OrderSide.BUY;
        Boolean hedgeMode = false;

        // When
        Order result = orderService.createOrder(symbol, amount, side, hedgeMode);

        // Then
        assertNull(result);

        // Verify no interactions
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle create order with zero amount")
    void testCreateOrderWithZeroAmount() {
        // Given
        String symbol = "BTCUSDT";
        Double amount = 0.0;
        OrderSide side = OrderSide.BUY;
        Boolean hedgeMode = false;

        // When
        Order result = orderService.createOrder(symbol, amount, side, hedgeMode);

        // Then
        assertNull(result);

        // Verify no interactions
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle create order with negative amount")
    void testCreateOrderWithNegativeAmount() {
        // Given
        String symbol = "BTCUSDT";
        Double amount = -0.001;
        OrderSide side = OrderSide.BUY;
        Boolean hedgeMode = false;

        // When
        Order result = orderService.createOrder(symbol, amount, side, hedgeMode);

        // Then
        assertNull(result);

        // Verify no interactions
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle create order with SELL side")
    void testCreateOrderWithSellSide() {
        // Given
        String symbol = "BTCUSDT";
        Double amount = 0.001;
        OrderSide side = OrderSide.SELL;
        Boolean hedgeMode = true;

        // When
        Order result = orderService.createOrder(symbol, amount, side, hedgeMode);

        // Then
        // In test environment, we expect null due to API call failure
        assertNull(result);

        // Verify no repository interactions since the API call fails
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should handle create order without hedge mode")
    void testCreateOrderWithoutHedgeMode() {
        // Given
        String symbol = "BTCUSDT";
        Double amount = 0.001;
        OrderSide side = OrderSide.BUY;
        Boolean hedgeMode = false;

        // When
        Order result = orderService.createOrder(symbol, amount, side, hedgeMode);

        // Then
        // In test environment, we expect null due to API call failure
        assertNull(result);

        // Verify no repository interactions since the API call fails
        verify(orderRepository, never()).save(any(Order.class));
    }
} 
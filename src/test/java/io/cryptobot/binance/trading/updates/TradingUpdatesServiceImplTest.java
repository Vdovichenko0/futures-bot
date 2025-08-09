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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingUpdatesServiceImplTest {

    @Mock
    private TradePlanGetService tradePlanGetService;
    
    @Mock
    private TradeSessionService sessionService;
    
    @Mock
    private OrderService orderService;

    private TradingUpdatesServiceImpl tradingUpdatesService;

    private TradeSession testSession;
    private TradePlan testTradePlan;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        tradingUpdatesService = new TradingUpdatesServiceImpl(tradePlanGetService, sessionService, orderService);
        
        // Создаем тестовую сессию
        testSession = TradeSession.builder()
                .id("test-session-id")
                .tradePlan("BTCUSDT")
                .orders(new ArrayList<>())
                .build();
        
        // Создаем тестовый план
        testTradePlan = TradePlan.builder()
                .symbol("BTCUSDT")
                .amountPerTrade(BigDecimal.valueOf(1000))
                .leverage(10)
                .sizes(SizeModel.builder()
                        .lotSize(BigDecimal.valueOf(0.001))
                        .build())
                .build();
        
        // Создаем тестовый ордер
        testOrder = Order.builder()
                .orderId(1001L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(50000))
                .commission(BigDecimal.valueOf(0.5))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();
    }

    @Test
    void testOpenPosition_Success() {
        // Given
        SessionMode sessionMode = SessionMode.SCALPING;
        TradingDirection direction = TradingDirection.LONG;
        OrderPurpose purpose = OrderPurpose.MAIN_OPEN;
        BigDecimal currentPrice = BigDecimal.valueOf(50000);
        String context = "test_context";
        Long parentOrderId = null;
        Long relatedHedgeId = null;

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.createOrder(eq("BTCUSDT"), anyDouble(), eq(OrderSide.BUY), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(1001L)).thenReturn(testOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        when(sessionService.addOrder(eq("test-session-id"), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                testSession, sessionMode, direction, purpose, currentPrice, 
                context, parentOrderId, relatedHedgeId
        );

        // Then
        assertNotNull(result);
        
        // Проверяем вызовы сервисов
        verify(tradePlanGetService).getPlan("BTCUSDT");
        verify(orderService).createOrder(eq("BTCUSDT"), anyDouble(), eq(OrderSide.BUY), eq(true));
        // getOrder вызывается в waitForFilledOrder, поэтому ожидаем минимум 1 вызов
        verify(orderService, atLeastOnce()).getOrder(1001L);
        
        // Проверяем создание TradeOrder
        ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(sessionService).addOrder(eq("test-session-id"), orderCaptor.capture());
        
        TradeOrder capturedOrder = orderCaptor.getValue();
        assertEquals(1001L, capturedOrder.getOrderId());
        assertEquals(purpose, capturedOrder.getPurpose());
        assertEquals(direction, capturedOrder.getDirection());
        assertEquals(context, capturedOrder.getCreationContext());
        assertEquals(sessionMode, capturedOrder.getModeAtCreation());
        assertEquals(parentOrderId, capturedOrder.getParentOrderId());
        assertEquals(relatedHedgeId, capturedOrder.getRelatedHedgeId());
        assertEquals("BTCUSDT", capturedOrder.getSymbol());
        assertEquals(OrderSide.BUY, capturedOrder.getSide());
        assertEquals("MARKET", capturedOrder.getType());
        assertEquals(BigDecimal.valueOf(0.1), capturedOrder.getCount());
        assertEquals(BigDecimal.valueOf(50000), capturedOrder.getPrice());
        assertEquals(BigDecimal.valueOf(0.5), capturedOrder.getCommission());
        assertEquals("USDT", capturedOrder.getCommissionAsset());
        assertEquals(OrderStatus.FILLED, capturedOrder.getStatus());
        assertEquals(10, capturedOrder.getLeverage());
    }

    @Test
    void testOpenPosition_ShortDirection() {
        // Given
        TradingDirection direction = TradingDirection.SHORT;
        OrderSide expectedSide = OrderSide.SELL;

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.createOrder(eq("BTCUSDT"), anyDouble(), eq(expectedSide), eq(true)))
                .thenReturn(testOrder);
        when(orderService.getOrder(1001L)).thenReturn(testOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                testSession, SessionMode.SCALPING, direction, OrderPurpose.HEDGE_OPEN, 
                BigDecimal.valueOf(50000), "test", 1000L, null
        );

        // Then
        assertNotNull(result);
        verify(orderService).createOrder(eq("BTCUSDT"), anyDouble(), eq(expectedSide), eq(true));
    }

    @Test
    void testOpenPosition_WithParentOrder() {
        // Given
        Long parentOrderId = 1000L;
        Long relatedHedgeId = 1002L;

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.createOrder(anyString(), anyDouble(), any(), anyBoolean()))
                .thenReturn(testOrder);
        when(orderService.getOrder(1001L)).thenReturn(testOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.openPosition(
                testSession, SessionMode.HEDGING, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN,
                BigDecimal.valueOf(50000), "hedge_context", parentOrderId, relatedHedgeId
        );

        // Then
        assertNotNull(result);
        
        ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(sessionService).addOrder(anyString(), orderCaptor.capture());
        
        TradeOrder capturedOrder = orderCaptor.getValue();
        assertEquals(parentOrderId, capturedOrder.getParentOrderId());
        assertEquals(relatedHedgeId, capturedOrder.getRelatedHedgeId());
        assertEquals(OrderPurpose.HEDGE_OPEN, capturedOrder.getPurpose());
        assertEquals(SessionMode.HEDGING, capturedOrder.getModeAtCreation());
    }

    @Test
    void testOpenPosition_InvalidQuantity() {
        // Given
        BigDecimal currentPrice = BigDecimal.valueOf(0.0001); // Очень низкая цена
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            tradingUpdatesService.openPosition(
                    testSession, SessionMode.SCALPING, TradingDirection.LONG, OrderPurpose.MAIN_OPEN,
                    currentPrice, "test", null, null
            );
        });
    }

    @Test
    void testClosePosition_Success() {
        // Given
        Long orderIdToClose = 1001L;
        Long relatedHedgeId = 1002L;
        TradingDirection direction = TradingDirection.LONG;
        OrderPurpose purpose = OrderPurpose.MAIN_CLOSE;
        BigDecimal currentPrice = BigDecimal.valueOf(51000);
        String context = "close_context";

        // Создаем ордер для закрытия
        TradeOrder orderToClose = TradeOrder.builder()
                .orderId(orderIdToClose)
                .direction(TradingDirection.LONG)
                .price(BigDecimal.valueOf(50000))
                .count(BigDecimal.valueOf(0.1))
                .side(OrderSide.BUY)
                .build();
        testSession.getOrders().add(orderToClose);

        // Создаем ордер закрытия
        Order closeOrder = Order.builder()
                .orderId(2001L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(51000))
                .commission(BigDecimal.valueOf(0.3))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.closeOrder(orderToClose)).thenReturn(closeOrder);
        when(orderService.getOrder(2001L)).thenReturn(closeOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                testSession, SessionMode.SCALPING, orderIdToClose, relatedHedgeId,
                direction, purpose, currentPrice, context
        );

        // Then
        assertNotNull(result);
        
        // Проверяем вызовы сервисов
        verify(tradePlanGetService).getPlan("BTCUSDT");
        verify(orderService).closeOrder(orderToClose);
        verify(orderService, atLeastOnce()).getOrder(2001L);
        
        // Проверяем создание TradeOrder
        ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(sessionService).addOrder(eq("test-session-id"), orderCaptor.capture());
        
        TradeOrder capturedOrder = orderCaptor.getValue();
        assertEquals(2001L, capturedOrder.getOrderId());
        assertEquals(purpose, capturedOrder.getPurpose());
        assertEquals(direction, capturedOrder.getDirection());
        assertEquals(context, capturedOrder.getCreationContext());
        assertEquals(SessionMode.SCALPING, capturedOrder.getModeAtCreation());
        assertEquals(orderIdToClose, capturedOrder.getParentOrderId());
        assertEquals(relatedHedgeId, capturedOrder.getRelatedHedgeId());
        assertEquals("BTCUSDT", capturedOrder.getSymbol());
        assertEquals(OrderSide.SELL, capturedOrder.getSide());
        assertEquals("MARKET", capturedOrder.getType());
        assertEquals(BigDecimal.valueOf(0.1), capturedOrder.getCount());
        assertEquals(BigDecimal.valueOf(51000), capturedOrder.getPrice());
        assertEquals(BigDecimal.valueOf(0.3), capturedOrder.getCommission());
        assertEquals("USDT", capturedOrder.getCommissionAsset());
        assertEquals(OrderStatus.FILLED, capturedOrder.getStatus());
        
        // Проверяем расчет PnL для LONG позиции
        // PnL = (51000 - 50000) / 50000 * 0.1 * 50000 = 0.02 * 5000 = 100
        BigDecimal expectedPnl = BigDecimal.valueOf(100).setScale(8, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expectedPnl.compareTo(capturedOrder.getPnl()));
    }

    @Test
    void testClosePosition_ShortDirection() {
        // Given
        Long orderIdToClose = 1001L;
        TradingDirection direction = TradingDirection.SHORT;
        
        // Создаем SHORT ордер для закрытия
        TradeOrder orderToClose = TradeOrder.builder()
                .orderId(orderIdToClose)
                .direction(TradingDirection.SHORT)
                .price(BigDecimal.valueOf(50000))
                .count(BigDecimal.valueOf(0.1))
                .side(OrderSide.SELL)
                .build();
        testSession.getOrders().add(orderToClose);

        // Создаем ордер закрытия
        Order closeOrder = Order.builder()
                .orderId(2001L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(49000))
                .commission(BigDecimal.valueOf(0.3))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.closeOrder(orderToClose)).thenReturn(closeOrder);
        when(orderService.getOrder(2001L)).thenReturn(closeOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                testSession, SessionMode.HEDGING, orderIdToClose, null,
                direction, OrderPurpose.HEDGE_CLOSE, BigDecimal.valueOf(49000), "close_short"
        );

        // Then
        assertNotNull(result);
        
        ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(sessionService).addOrder(anyString(), orderCaptor.capture());
        
        TradeOrder capturedOrder = orderCaptor.getValue();
        assertEquals(OrderPurpose.HEDGE_CLOSE, capturedOrder.getPurpose());
        assertEquals(TradingDirection.SHORT, capturedOrder.getDirection());
        assertEquals(SessionMode.HEDGING, capturedOrder.getModeAtCreation());
        
        // Проверяем расчет PnL для SHORT позиции
        // PnL = (50000 - 49000) / 50000 * 0.1 * 50000 = 0.02 * 5000 = 100
        BigDecimal expectedPnl = BigDecimal.valueOf(100).setScale(8, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expectedPnl.compareTo(capturedOrder.getPnl()));
    }

    @Test
    void testClosePosition_OrderNotFound() {
        // Given
        Long nonExistentOrderId = 9999L;

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            tradingUpdatesService.closePosition(
                    testSession, SessionMode.SCALPING, nonExistentOrderId, null,
                    TradingDirection.LONG, OrderPurpose.MAIN_CLOSE, BigDecimal.valueOf(50000), "test"
            );
        });
    }

    @Test
    void testClosePosition_WithRelatedHedgeId() {
        // Given
        Long orderIdToClose = 1001L;
        Long relatedHedgeId = 1002L;
        
        TradeOrder orderToClose = TradeOrder.builder()
                .orderId(orderIdToClose)
                .direction(TradingDirection.LONG)
                .price(BigDecimal.valueOf(50000))
                .count(BigDecimal.valueOf(0.1))
                .side(OrderSide.BUY)
                .build();
        testSession.getOrders().add(orderToClose);

        Order closeOrder = Order.builder()
                .orderId(2001L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(51000))
                .commission(BigDecimal.valueOf(0.3))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.closeOrder(orderToClose)).thenReturn(closeOrder);
        when(orderService.getOrder(2001L)).thenReturn(closeOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                testSession, SessionMode.HEDGING, orderIdToClose, relatedHedgeId,
                TradingDirection.LONG, OrderPurpose.HEDGE_CLOSE, BigDecimal.valueOf(51000), "close_with_hedge"
        );

        // Then
        assertNotNull(result);
        
        ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(sessionService).addOrder(anyString(), orderCaptor.capture());
        
        TradeOrder capturedOrder = orderCaptor.getValue();
        assertEquals(orderIdToClose, capturedOrder.getParentOrderId());
        assertEquals(relatedHedgeId, capturedOrder.getRelatedHedgeId());
        assertEquals(OrderPurpose.HEDGE_CLOSE, capturedOrder.getPurpose());
    }

    @Test
    void testOpenPosition_OrderNotFilled() {
        // Given
        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.createOrder(anyString(), anyDouble(), any(), anyBoolean()))
                .thenReturn(testOrder);
        
        // Симулируем, что ордер не заполнен
        Order unfilledOrder = Order.builder()
                .orderId(1001L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(50000))
                .commission(BigDecimal.valueOf(0.5))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.NEW)
                .tradeTime(System.currentTimeMillis())
                .build();
        when(orderService.getOrder(1001L)).thenReturn(unfilledOrder);
        
        // When
        TradeSession result = tradingUpdatesService.openPosition(
                testSession, SessionMode.SCALPING, TradingDirection.LONG, OrderPurpose.MAIN_OPEN,
                BigDecimal.valueOf(50000), "test", null, null
        );

        // Then
        assertEquals(testSession, result); // Возвращается исходная сессия
        verify(sessionService, never()).addOrder(anyString(), any(TradeOrder.class));
    }

    @Test
    void testClosePosition_OrderNotFilled() {
        // Given
        Long orderIdToClose = 1001L;
        
        TradeOrder orderToClose = TradeOrder.builder()
                .orderId(orderIdToClose)
                .direction(TradingDirection.LONG)
                .price(BigDecimal.valueOf(50000))
                .count(BigDecimal.valueOf(0.1))
                .side(OrderSide.BUY)
                .build();
        testSession.getOrders().add(orderToClose);

        Order closeOrder = Order.builder()
                .orderId(2001L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(51000))
                .commission(BigDecimal.valueOf(0.3))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.NEW) // Не заполнен
                .tradeTime(System.currentTimeMillis())
                .build();

        when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        when(orderService.closeOrder(orderToClose)).thenReturn(closeOrder);
        when(orderService.getOrder(2001L)).thenReturn(closeOrder);

        // When
        TradeSession result = tradingUpdatesService.closePosition(
                testSession, SessionMode.SCALPING, orderIdToClose, null,
                TradingDirection.LONG, OrderPurpose.MAIN_CLOSE, BigDecimal.valueOf(51000), "test"
        );

        // Then
        assertEquals(testSession, result); // Возвращается исходная сессия
        verify(sessionService, never()).addOrder(anyString(), any(TradeOrder.class));
    }

    @Test
    void testOpenPosition_DifferentSessionModes() {
        // Given
        SessionMode[] modes = {SessionMode.SCALPING, SessionMode.HEDGING};
        
        lenient().when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        lenient().when(orderService.createOrder(anyString(), anyDouble(), any(), anyBoolean()))
                .thenReturn(testOrder);
        lenient().when(orderService.getOrder(1001L)).thenReturn(testOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        lenient().when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When & Then
        for (SessionMode mode : modes) {
            TradeSession result = tradingUpdatesService.openPosition(
                    testSession, mode, TradingDirection.LONG, OrderPurpose.MAIN_OPEN,
                    BigDecimal.valueOf(50000), "test_mode_" + mode, null, null
            );
            
            assertNotNull(result);
            
            ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
            verify(sessionService).addOrder(anyString(), orderCaptor.capture());
            
            TradeOrder capturedOrder = orderCaptor.getValue();
            assertEquals(mode, capturedOrder.getModeAtCreation());
            
            // Сбрасываем моки для следующей итерации
            reset(sessionService);
            lenient().when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                    .thenReturn(updatedSession);
        }
    }

    @Test
    void testClosePosition_DifferentPurposes() {
        // Given
        OrderPurpose[] purposes = {OrderPurpose.MAIN_CLOSE, OrderPurpose.HEDGE_CLOSE, OrderPurpose.MAIN_PARTIAL_CLOSE, OrderPurpose.HEDGE_PARTIAL_CLOSE};
        
        TradeOrder orderToClose = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .price(BigDecimal.valueOf(50000))
                .count(BigDecimal.valueOf(0.1))
                .side(OrderSide.BUY)
                .build();
        testSession.getOrders().add(orderToClose);

        Order closeOrder = Order.builder()
                .orderId(2001L)
                .symbol("BTCUSDT")
                .side(OrderSide.SELL)
                .orderType("MARKET")
                .quantity(BigDecimal.valueOf(0.1))
                .averagePrice(BigDecimal.valueOf(51000))
                .commission(BigDecimal.valueOf(0.3))
                .commissionAsset("USDT")
                .orderStatus(OrderStatus.FILLED)
                .tradeTime(System.currentTimeMillis())
                .build();

        lenient().when(tradePlanGetService.getPlan("BTCUSDT")).thenReturn(testTradePlan);
        lenient().when(orderService.closeOrder(orderToClose)).thenReturn(closeOrder);
        lenient().when(orderService.getOrder(2001L)).thenReturn(closeOrder);
        
        TradeSession updatedSession = testSession.toBuilder().build();
        lenient().when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                .thenReturn(updatedSession);

        // When & Then
        for (OrderPurpose purpose : purposes) {
            TradeSession result = tradingUpdatesService.closePosition(
                    testSession, SessionMode.SCALPING, 1001L, null,
                    TradingDirection.LONG, purpose, BigDecimal.valueOf(51000), "test_purpose_" + purpose
            );
            
            assertNotNull(result);
            
            ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
            verify(sessionService).addOrder(anyString(), orderCaptor.capture());
            
            TradeOrder capturedOrder = orderCaptor.getValue();
            assertEquals(purpose, capturedOrder.getPurpose());
            
            // Сбрасываем моки для следующей итерации
            reset(sessionService);
            lenient().when(sessionService.addOrder(anyString(), any(TradeOrder.class)))
                    .thenReturn(updatedSession);
        }
    }
} 
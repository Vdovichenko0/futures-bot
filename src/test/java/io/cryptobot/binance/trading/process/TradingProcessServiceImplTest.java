package io.cryptobot.binance.trading.process;

import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.enums.OrderType;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trading.monitoring.v3.MonitoringServiceV3;
import io.cryptobot.binance.trading.process.TradingProcessService;
import io.cryptobot.binance.trading.process.TradingProcessServiceImpl;
import io.cryptobot.configs.locks.TradePlanLockRegistry;
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
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingProcessServiceImpl Tests")
class TradingProcessServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private MonitoringServiceV3 monitoringService;

    @Mock
    private TradePlanLockRegistry lockRegistry;

    @InjectMocks
    private TradingProcessServiceImpl tradingProcessService;

    private TradePlan testTradePlan;
    private Order testOrder;
    private TradeSession testTradeSession;
    private SizeModel testSizeModel;
    private ReentrantLock testLock;

    @BeforeEach
    void setUp() {
        testSizeModel = SizeModel.builder()
                .tickSize(new BigDecimal("0.1"))
                .lotSize(new BigDecimal("0.001"))
                .minCount(new BigDecimal("0.001"))
                .minAmount(new BigDecimal("10"))
                .build();

        testTradePlan = TradePlan.builder()
                .symbol("BTCUSDT")
                .amountPerTrade(new BigDecimal("100"))
                .sizes(testSizeModel)
                .build();

        testOrder = Order.builder()
                .orderId(123456789L)
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .quantity(new BigDecimal("0.002"))
                .orderStatus(OrderStatus.FILLED)
                .build();

        testTradeSession = TradeSession.builder()
                .id("test-session")
                .tradePlan("BTCUSDT")
                .status(io.cryptobot.binance.trade.session.enums.SessionStatus.ACTIVE)
                .mainPosition(123456789L)
                .orders(new java.util.ArrayList<>())
                .currentMode(io.cryptobot.binance.trade.session.enums.SessionMode.SCALPING)
                .direction(TradingDirection.LONG)
                .pnl(BigDecimal.ZERO)
                .totalCommission(BigDecimal.ZERO)
                .pnlTotal(BigDecimal.ZERO)
                .hedgeOpenCount(0)
                .hedgeCloseCount(0)
                .countAverageOrders(0)
                .activeLong(true)
                .activeShort(false)
                .activeAverageLong(false)
                .activeAverageShort(false)
                .processing(false)
                .createdTime(LocalDateTime.now())
                .build();

        testLock = new ReentrantLock();

        // Устанавливаем короткие значения для тестов
        tradingProcessService.setMaxWaitMillis(100);
        tradingProcessService.setIntervalMillis(50);

        // Настраиваем мок для lockRegistry
        lenient().when(lockRegistry.getLock(any())).thenReturn(testLock);
    }

    @Test
    @DisplayName("Should set and get interval milliseconds")
    void shouldSetAndGetIntervalMillis() {
        // Given
        int expectedInterval = 5000;

        // When
        tradingProcessService.setIntervalMillis(expectedInterval);

        // Then
        assertEquals(expectedInterval, tradingProcessService.getIntervalMillis());
    }

    @Test
    @DisplayName("Should set and get max wait milliseconds")
    void shouldSetAndGetMaxWaitMillis() {
        // Given
        int expectedMaxWait = 30000;

        // When
        tradingProcessService.setMaxWaitMillis(expectedMaxWait);

        // Then
        assertEquals(expectedMaxWait, tradingProcessService.getMaxWaitMillis());
    }

    @Test
    @DisplayName("Should handle basic order creation")
    void shouldHandleBasicOrderCreation() {
        // Given
        TradingDirection direction = TradingDirection.LONG;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createLimitOrElseMarket(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(testSizeModel)))
                .thenReturn(testOrder);

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createLimitOrElseMarket(eq("BTCUSDT"), eq(0.002), eq(OrderSide.BUY), eq(testSizeModel));
    }

    @Test
    @DisplayName("Should handle basic order creation for SHORT direction")
    void shouldHandleBasicOrderCreationForShort() {
        // Given
        TradingDirection direction = TradingDirection.SHORT;
        BigDecimal currentPrice = new BigDecimal("50000.00");
        String context = "test-context";

        when(orderService.createLimitOrElseMarket(eq("BTCUSDT"), eq(0.002), eq(OrderSide.SELL), eq(testSizeModel)))
                .thenReturn(testOrder);

        // When
        tradingProcessService.openOrder(testTradePlan, direction, currentPrice, context);

        // Then
        verify(orderService).createLimitOrElseMarket(eq("BTCUSDT"), eq(0.002), eq(OrderSide.SELL), eq(testSizeModel));
    }
} 
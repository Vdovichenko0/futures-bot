package io.cryptobot.binance.trading.monitoring;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceImplTest {

    @Mock
    private TradeSessionService sessionService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private TradingUpdatesService tradingUpdatesService;

    private MonitoringServiceImpl monitoring;

    private static final String SYMBOL = "COINUSDT";
    
    // === КОНСТАНТЫ ТЕСТИРОВАНИЯ ===
    // Копируем константы из MonitoringServiceImpl для тестирования
    private static final BigDecimal TRAILING_ACTIVATION_THRESHOLD = BigDecimal.valueOf(0.15); // Активация трейлинга 0.15%
    private static final BigDecimal TRAILING_RETRACE_RATIO = BigDecimal.valueOf(0.8);         // Откат трейлинга 80%
    private static final BigDecimal TRACKING_ACTIVATION_THRESHOLD = BigDecimal.valueOf(-0.3); // Активация отслеживания -0.3%
    private static final BigDecimal WORSENING_THRESHOLD = BigDecimal.valueOf(-0.1);           // Порог ухудшения -0.1%
    private static final BigDecimal IMPROVEMENT_THRESHOLD = BigDecimal.valueOf(0.1);          // Порог улучшения 0.1%
    private static final BigDecimal PULLBACK_RATIO = BigDecimal.valueOf(0.7);                 // Соотношение отката 70%

    @BeforeEach
    void setUp() {
        monitoring = new MonitoringServiceImpl(sessionService, ticker24hService, tradingUpdatesService);
    }

    // Helper: create FILLED TradeOrder
    private TradeOrder createFilledOrder(long id,
                                         OrderPurpose purpose,
                                         TradingDirection direction,
                                         OrderSide side,
                                         BigDecimal price) {
        return TradeOrder.builder()
                .orderId(id)
                .purpose(purpose)
                .direction(direction)
                .symbol(SYMBOL)
                .side(side)
                .type("MARKET")
                .count(BigDecimal.valueOf(1))
                .price(price)
                .commission(BigDecimal.ZERO)
                .commissionAsset("USDT")
                .status(OrderStatus.FILLED)
                .pnl(BigDecimal.ZERO)
                .leverage(10)
                .orderTime(LocalDateTime.now())
                .trailingActive(false)
                .pnlHigh(BigDecimal.ZERO)
                .build();
    }

    // Helper: base TradeSession with MAIN_OPEN already added via onCreate
    private TradeSession createSessionWithMain(long mainId,
                                               TradingDirection mainDirection,
                                               BigDecimal entryPrice) {
        TradeOrder mainOrder = createFilledOrder(
                mainId,
                OrderPurpose.MAIN_OPEN,
                mainDirection,
                mainDirection == TradingDirection.LONG ? OrderSide.BUY : OrderSide.SELL,
                entryPrice
        );

        TradeSession session = TradeSession.builder()
                .id("s-" + mainId)
                .tradePlan(SYMBOL)
                .orders(new ArrayList<>())
                .build();
        session.onCreate(SYMBOL, mainDirection, mainOrder, "entry");
        return session;
    }

    @Test
    void singlePosition_short_trailingClose() {
        // Given: MAIN SHORT at 2.9530
        BigDecimal entry = new BigDecimal("2.9530");
        TradeSession session = createSessionWithMain(1L, TradingDirection.SHORT, entry);
        monitoring.addToMonitoring(session);

        // Price sequence: down to 2.9470 (activate trailing), then retrace to 2.9489 (trigger close), extra read for close uses 2.9480
        when(ticker24hService.getPrice(SYMBOL)).thenReturn(new BigDecimal("2.9470"), new BigDecimal("2.9489"), new BigDecimal("2.9480"));

        ArgumentCaptor<TradeSession> sessionArg = ArgumentCaptor.forClass(TradeSession.class);
        ArgumentCaptor<Long> idArg = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TradingDirection> dirArg = ArgumentCaptor.forClass(TradingDirection.class);
        ArgumentCaptor<OrderPurpose> purposeArg = ArgumentCaptor.forClass(OrderPurpose.class);
        ArgumentCaptor<BigDecimal> priceArg = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> contextArg = ArgumentCaptor.forClass(String.class);

        TradeSession updated = session.toBuilder().status(SessionStatus.COMPLETED).build();
        when(tradingUpdatesService.closePosition(
                any(), any(), anyLong(), any(), any(), any(), any(), any()
        )).thenReturn(updated);

        // When: two monitoring ticks
        monitoring.monitor();
        monitoring.monitor();

        // Then
        verify(ticker24hService, atLeast(2)).getPrice(SYMBOL);
        verify(tradingUpdatesService, times(1)).closePosition(
                sessionArg.capture(), eq(SessionMode.SCALPING), idArg.capture(), any(), dirArg.capture(), purposeArg.capture(), priceArg.capture(), contextArg.capture()
        );

        assertTrue(sessionArg.getValue().isProcessing());
        assertEquals(1L, idArg.getValue());
        assertEquals(TradingDirection.SHORT, dirArg.getValue());
        assertEquals(OrderPurpose.MAIN_CLOSE, purposeArg.getValue());
        // Price passed to closePosition is taken from an extra getPrice call
        assertEquals(new BigDecimal("2.9480"), priceArg.getValue());
        assertTrue(contextArg.getValue().startsWith("monitoring_trailing"));

        monitoring.monitor();
        verifyNoMoreInteractions(tradingUpdatesService);
    }

    @Test
    void singlePosition_worsening_opensHedgeOppositeDirection() {
        // Given: MAIN SHORT at 2.9459
        BigDecimal entry = new BigDecimal("2.9459");
        TradeSession session = createSessionWithMain(10L, TradingDirection.SHORT, entry);
        monitoring.addToMonitoring(session);

        // Price sequence: up to >= +0.3% (base), then further worsen by >=0.1%
        BigDecimal price1 = entry.multiply(new BigDecimal("1.0032")); // base
        BigDecimal price2 = entry.multiply(new BigDecimal("1.0043")); // worsen
        when(ticker24hService.getPrice(SYMBOL)).thenReturn(price1, price2);

        ArgumentCaptor<TradeSession> sessionArg = ArgumentCaptor.forClass(TradeSession.class);
        ArgumentCaptor<TradingDirection> dirArg = ArgumentCaptor.forClass(TradingDirection.class);
        ArgumentCaptor<OrderPurpose> purposeArg = ArgumentCaptor.forClass(OrderPurpose.class);
        ArgumentCaptor<BigDecimal> priceArg = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<Long> parentIdArg = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> contextArg = ArgumentCaptor.forClass(String.class);

        TradeOrder hedgeLong = createFilledOrder(11L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, OrderSide.BUY, price2);
        TradeSession updated = session.toBuilder().build();
        updated.addOrder(hedgeLong);
        when(tradingUpdatesService.openPosition(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(updated);

        monitoring.monitor(); // base tracking
        monitoring.monitor(); // open hedge

        verify(tradingUpdatesService, times(1)).openPosition(
                sessionArg.capture(), eq(SessionMode.HEDGING), dirArg.capture(), purposeArg.capture(), priceArg.capture(), contextArg.capture(), parentIdArg.capture(), isNull()
        );

        assertTrue(sessionArg.getValue().isProcessing());
        assertEquals(TradingDirection.LONG, dirArg.getValue());
        assertEquals(OrderPurpose.HEDGE_OPEN, purposeArg.getValue());
        assertEquals(price2, priceArg.getValue());
        assertEquals(10L, parentIdArg.getValue());
        assertTrue(contextArg.getValue().startsWith("monitoring_worsening"));

        verify(tradingUpdatesService, never()).closePosition(any(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void twoPositions_bestTrailingClosesHedge_thenNoNewHedgeWhileBothActive() {
        // Given: MAIN SHORT at 100, HEDGE LONG at 101
        TradeSession session = createSessionWithMain(100L, TradingDirection.SHORT, new BigDecimal("100"));
        TradeOrder hedgeLong = createFilledOrder(200L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, OrderSide.BUY, new BigDecimal("101"));
        session.addOrder(hedgeLong);
        assertTrue(session.hasBothPositionsActive());
        monitoring.addToMonitoring(session);

        // Price sequence: activate trailing on LONG, then retrace; extra read for close price
        when(ticker24hService.getPrice(SYMBOL)).thenReturn(new BigDecimal("101.5"), new BigDecimal("101.1"), new BigDecimal("101.1"));

        TradeSession updatedAfterClose = session.toBuilder().build();
        TradeOrder closeHedge = createFilledOrder(201L, OrderPurpose.HEDGE_CLOSE, TradingDirection.LONG, OrderSide.SELL, new BigDecimal("101.1"));
        closeHedge = closeHedge.toBuilder().parentOrderId(200L).build();
        updatedAfterClose.addOrder(closeHedge);
        when(tradingUpdatesService.closePosition(any(), any(), eq(200L), any(), eq(TradingDirection.LONG), eq(OrderPurpose.HEDGE_CLOSE), any(), any()))
                .thenReturn(updatedAfterClose);

        monitoring.monitor();
        monitoring.monitor();

        verify(tradingUpdatesService, times(1)).closePosition(
                any(), eq(SessionMode.HEDGING), eq(200L), any(), eq(TradingDirection.LONG), eq(OrderPurpose.HEDGE_CLOSE), eq(new BigDecimal("101.1")), any()
        );

        verify(tradingUpdatesService, never()).openPosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mainClosed_singleActiveHedge_parentLinkToLastOppositeHedge() {
        // Given: MAIN LONG at 100, then HEDGE SHORT at 99, then MAIN CLOSE
        TradeSession session = createSessionWithMain(500L, TradingDirection.LONG, new BigDecimal("100"));
        TradeOrder hedgeShort = createFilledOrder(600L, OrderPurpose.HEDGE_OPEN, TradingDirection.SHORT, OrderSide.SELL, new BigDecimal("99"));
        session.addOrder(hedgeShort);
        TradeOrder mainClose = createFilledOrder(501L, OrderPurpose.MAIN_CLOSE, TradingDirection.LONG, OrderSide.SELL, new BigDecimal("101"))
                .toBuilder().parentOrderId(500L).build();
        session.addOrder(mainClose);
        assertFalse(session.isActiveLong());
        assertTrue(session.isActiveShort());
        monitoring.addToMonitoring(session);

        BigDecimal p1 = new BigDecimal("101.0");
        BigDecimal p2 = new BigDecimal("101.5");
        when(ticker24hService.getPrice(SYMBOL)).thenReturn(p1, p2);

        ArgumentCaptor<Long> parentArg = ArgumentCaptor.forClass(Long.class);

        TradeOrder newLong = createFilledOrder(700L, OrderPurpose.HEDGE_OPEN, TradingDirection.LONG, OrderSide.BUY, p2);
        TradeSession updated = session.toBuilder().build();
        updated.addOrder(newLong);
        when(tradingUpdatesService.openPosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updated);

        monitoring.monitor();
        monitoring.monitor();

        verify(tradingUpdatesService).openPosition(
                any(), eq(SessionMode.HEDGING), eq(TradingDirection.LONG), eq(OrderPurpose.HEDGE_OPEN), eq(p2), contains("monitoring_worsening"), parentArg.capture(), isNull()
        );
        assertEquals(600L, parentArg.getValue());
    }
}
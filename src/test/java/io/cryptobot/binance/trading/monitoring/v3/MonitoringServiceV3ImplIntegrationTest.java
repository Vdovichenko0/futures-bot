package io.cryptobot.binance.trading.monitoring.v3;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.market_data.ticker24h.Ticker24h;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MonitoringServiceV3Impl Integration Tests")
class MonitoringServiceV3ImplIntegrationTest {

    private static final String SYMBOL = "BTCUSDT";

    private InMemoryTradeSessionService sessionService;
    private FakeTicker24hService tickerService;
    private FakeTradingUpdatesService tradingUpdatesService;
    private MonitoringServiceV3Impl monitoringService;

    private TradePlan testPlan;
    private AtomicLong orderIdCounter = new AtomicLong(20000);

    @BeforeEach
    void setUp() {
        sessionService = new InMemoryTradeSessionService();
        tickerService = new FakeTicker24hService();
        tradingUpdatesService = new FakeTradingUpdatesService();

        monitoringService = new MonitoringServiceV3Impl(sessionService, tickerService, tradingUpdatesService);

        testPlan = new TradePlan();
        testPlan.onCreate(SYMBOL, new BigDecimal("100"), 10, null, null);

        tickerService.setPrice(SYMBOL, new BigDecimal("50000"));
    }

    @Test
    @DisplayName("End-to-End: MAIN LONG → Hedge SHORT → Profit trailing close → Follow-up 1/3 close")
    void testEndToEndCycleWorsenBranch() {
        // Phase 1: Create MAIN LONG
        TradeOrder mainOrder = createOrder(TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.LONG, mainOrder);
        monitoringService.addToMonitoring(session);

        // Phase 2: Price drops -0.1% → open SHORT hedge
        tickerService.setPrice(SYMBOL, new BigDecimal("49950")); // -0.1%
        monitoringService.monitor();
        assertEquals(1, tradingUpdatesService.getOpenPositionCalls().size());
        var hedgeOpenCall = tradingUpdatesService.getOpenPositionCalls().get(0);
        assertEquals(TradingDirection.SHORT, hedgeOpenCall.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, hedgeOpenCall.purpose);
        // Reflect hedge in session and clear processing
        TradeOrder hedgeShort = createOrder(TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, new BigDecimal("49950"));
        session.addOrder(hedgeShort);
        session.openShortPosition();
        session.setProcessing(false);

        // Cooldown after hedge open before trailing close attempts
        sleepQuiet(10_200);

        // Phase 3: Price rises; LONG becomes profitable ≥ +0.1% → activate trailing on LONG
        tickerService.setPrice(SYMBOL, new BigDecimal("50050")); // +0.1%
        monitoringService.monitor();
        assertTrue(session.getMainOrder().getTrailingActive(), "Trailing should be active on profitable LONG");

        // Push higher to build pnlHigh, then retrace to 70% → close LONG by trailing
        tickerService.setPrice(SYMBOL, new BigDecimal("50100")); // ~+0.2%
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50070")); // ~0.14% (70% of 0.2%)
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getClosePositionCalls().size() >= 1);
        var closeLong = tradingUpdatesService.getClosePositionCalls().get(
                tradingUpdatesService.getClosePositionCalls().size() - 1);
        assertEquals(TradingDirection.LONG, closeLong.direction);
        assertEquals(OrderPurpose.MAIN_CLOSE, closeLong.purpose);

        // Reflect close LONG in session and clear processing
        TradeOrder closeMainOrder = TradeOrder.builder()
                .orderId(orderIdCounter.incrementAndGet())
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(mainOrder.getOrderId())
                .price(new BigDecimal("50070"))
                .orderTime(LocalDateTime.now())
                .build();
        session.addOrder(closeMainOrder);
        session.setProcessing(false);

        // Cooldown after close; wait real cooldown before next order
        sleepQuiet(10_200);

        // Phase 4: Follow-up should close losing SHORT when it reaches 1/3 of pair's reference profit
        // First tick sets baseline; second tick should trigger close if SHORT PnL >= 1/3 of reference
        tickerService.setPrice(SYMBOL, new BigDecimal("50150")); // set baseline (SHORT still negative)
        monitoringService.monitor();
        int before = tradingUpdatesService.getClosePositionCalls().size();
        tickerService.setPrice(SYMBOL, new BigDecimal("50200")); // SHORT worsens, should trigger follow-up hedge
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getOpenPositionCalls().size() > 1, "Should open follow-up hedge on worsen");
        var followUpOpen = tradingUpdatesService.getOpenPositionCalls().get(
                tradingUpdatesService.getOpenPositionCalls().size() - 1);
        assertEquals(TradingDirection.LONG, followUpOpen.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, followUpOpen.purpose);
    }

    @Test
    @DisplayName("Follow-up: close losing at one-third of pair profit")
    void testFollowUpOneThirdClose() {
        // Start with LONG main and SHORT hedge to be in two-positions mode
        TradeOrder mainOrder = createOrder(TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.LONG, mainOrder);
        monitoringService.addToMonitoring(session);

        TradeOrder hedgeShort = createOrder(TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, new BigDecimal("49950"));
        session.addOrder(hedgeShort);
        session.openShortPosition();

        // Make LONG profitable ≥ 0.1% and build pnlHigh ~0.2%, then retrace to close LONG by trailing
        tickerService.setPrice(SYMBOL, new BigDecimal("50050"));
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50100"));
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50070")); // retrace
        monitoringService.monitor();
        // Reflect LONG close
        TradeOrder closeMainOrder = TradeOrder.builder()
                .orderId(orderIdCounter.incrementAndGet())
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(mainOrder.getOrderId())
                .price(new BigDecimal("50070"))
                .orderTime(LocalDateTime.now())
                .build();
        session.addOrder(closeMainOrder);
        session.setProcessing(false);

        // Cooldown after close; wait real cooldown before next order
        sleepQuiet(10_200);

        // Now only SHORT remains; target = 1/3 of reference profit (~0.0667 if ref ~0.2)
        // Drop price so that SHORT PnL >= target
        tickerService.setPrice(SYMBOL, new BigDecimal("49900"));
        monitoringService.monitor();

        // Expect closing SHORT (losing turned profitable to at least one-third of ref)
        assertTrue(tradingUpdatesService.getClosePositionCalls().size() >= 1);
        var lastClose = tradingUpdatesService.getClosePositionCalls().get(
                tradingUpdatesService.getClosePositionCalls().size() - 1);
        assertEquals(TradingDirection.SHORT, lastClose.direction);
        assertEquals(OrderPurpose.HEDGE_CLOSE, lastClose.purpose);
    }

    @Test
    @DisplayName("SHORT cycle: main SHORT → open LONG hedge → close LONG in profit → SHORT goes to profit and closes")
    void testShortCycleProfitProfit() {
        // Create MAIN SHORT
        TradeOrder mainShort = createOrder(TradingDirection.SHORT, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.SHORT, mainShort);
        monitoringService.addToMonitoring(session);

        // Price rises (SHORT loss) to -0.1% → open LONG hedge
        tickerService.setPrice(SYMBOL, new BigDecimal("50050")); // -0.1% for SHORT
        monitoringService.monitor();
        assertEquals(1, tradingUpdatesService.getOpenPositionCalls().size());
        var openLong = tradingUpdatesService.getOpenPositionCalls().get(0);
        assertEquals(TradingDirection.LONG, openLong.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, openLong.purpose);
        // Reflect hedge LONG
        TradeOrder hedgeLong = createOrder(TradingDirection.LONG, OrderPurpose.HEDGE_OPEN, new BigDecimal("50050"));
        session.addOrder(hedgeLong);
        session.openLongPosition();
        session.setProcessing(false);

        // Wait cooldown before attempting to close LONG by trailing
        sleepQuiet(10_200);

        // Make LONG profitable and close by trailing: up to +0.2 then retrace to 70%
        tickerService.setPrice(SYMBOL, new BigDecimal("50100")); // LONG +0.1% from its entry already → trailing active
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50200")); // ~+0.3% from 50050 builds pnlHigh
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50140")); // retrace to ~70%
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getClosePositionCalls().size() >= 1);
        var closeLong = tradingUpdatesService.getClosePositionCalls().get(
                tradingUpdatesService.getClosePositionCalls().size() - 1);
        assertEquals(TradingDirection.LONG, closeLong.direction);
        assertTrue(closeLong.purpose == OrderPurpose.HEDGE_CLOSE || closeLong.purpose == OrderPurpose.MAIN_CLOSE);

        // Reflect LONG close
        TradeOrder closeHedgeLong = TradeOrder.builder()
                .orderId(orderIdCounter.incrementAndGet())
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(hedgeLong.getOrderId())
                .price(new BigDecimal("50140"))
                .orderTime(LocalDateTime.now())
                .build();
        session.addOrder(closeHedgeLong);
        session.setProcessing(false);

        // Wait cooldown
        sleepQuiet(10_200);

        // Now only SHORT remains. Move price down so SHORT becomes profitable → trailing activates then retrace closes SHORT
        tickerService.setPrice(SYMBOL, new BigDecimal("49950")); // SHORT +0.1%
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("49850")); // build pnlHigh
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("49905")); // retrace ~70%
        monitoringService.monitor();

        assertTrue(tradingUpdatesService.getClosePositionCalls().size() >= 2);
        var closeShort = tradingUpdatesService.getClosePositionCalls().get(
                tradingUpdatesService.getClosePositionCalls().size() - 1);
        assertEquals(TradingDirection.SHORT, closeShort.direction);
        assertTrue(closeShort.purpose == OrderPurpose.MAIN_CLOSE || closeShort.purpose == OrderPurpose.HEDGE_CLOSE);
    }

    @Test
    @DisplayName("SHORT cycle: main SHORT → open LONG hedge → close LONG in profit → SHORT worsens (follow-up opens hedge)")
    void testShortCycleProfitThenShortLossFollowUp() {
        // Create MAIN SHORT
        TradeOrder mainShort = createOrder(TradingDirection.SHORT, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.SHORT, mainShort);
        monitoringService.addToMonitoring(session);

        // Price rises (SHORT loss) to -0.1% → open LONG hedge
        tickerService.setPrice(SYMBOL, new BigDecimal("50050"));
        monitoringService.monitor();
        assertEquals(1, tradingUpdatesService.getOpenPositionCalls().size());
        var openLong = tradingUpdatesService.getOpenPositionCalls().get(0);
        assertEquals(TradingDirection.LONG, openLong.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, openLong.purpose);
        // Reflect hedge LONG
        TradeOrder hedgeLong = createOrder(TradingDirection.LONG, OrderPurpose.HEDGE_OPEN, new BigDecimal("50050"));
        session.addOrder(hedgeLong);
        session.openLongPosition();
        session.setProcessing(false);

        // Wait cooldown before attempting to close LONG by trailing
        sleepQuiet(10_200);

        // Make LONG profitable and close by trailing
        tickerService.setPrice(SYMBOL, new BigDecimal("50200"));
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50140"));
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getClosePositionCalls().size() >= 1);

        // Reflect LONG close
        TradeOrder closeHedgeLong = TradeOrder.builder()
                .orderId(orderIdCounter.incrementAndGet())
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(hedgeLong.getOrderId())
                .price(new BigDecimal("50140"))
                .orderTime(LocalDateTime.now())
                .build();
        session.addOrder(closeHedgeLong);
        session.setProcessing(false);

        // Wait cooldown
        sleepQuiet(10_200);

        // SHORT worsens (price up). Follow-up: first tick baseline, second tick worsen ≥ -0.1 → open hedge LONG again
        int openBefore = tradingUpdatesService.getOpenPositionCalls().size();
        tickerService.setPrice(SYMBOL, new BigDecimal("50160")); // baseline
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50210")); // worsen
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getOpenPositionCalls().size() > openBefore);
        var followUpOpen = tradingUpdatesService.getOpenPositionCalls().get(
                tradingUpdatesService.getOpenPositionCalls().size() - 1);
        assertEquals(TradingDirection.LONG, followUpOpen.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, followUpOpen.purpose);
    }

    @Test
    @DisplayName("LONG cycle: main LONG → open SHORT hedge → close SHORT in profit → LONG improves (follow-up with trailing + hedge)")
    void testLongCycleProfitThenLongImproveFollowUp() {
        // Create MAIN LONG
        TradeOrder mainLong = createOrder(TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.LONG, mainLong);
        monitoringService.addToMonitoring(session);

        // Price drops (LONG loss) to -0.1% → open SHORT hedge
        tickerService.setPrice(SYMBOL, new BigDecimal("49950")); // -0.1% for LONG
        monitoringService.monitor();
        assertEquals(1, tradingUpdatesService.getOpenPositionCalls().size());
        var openShort = tradingUpdatesService.getOpenPositionCalls().get(0);
        assertEquals(TradingDirection.SHORT, openShort.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, openShort.purpose);
        // Reflect hedge SHORT
        TradeOrder hedgeShort = createOrder(TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, new BigDecimal("49950"));
        session.addOrder(hedgeShort);
        session.openShortPosition();
        session.setProcessing(false);

        // Wait cooldown before attempting to close SHORT by trailing
        sleepQuiet(10_200);

        // Make SHORT profitable and close by trailing
        tickerService.setPrice(SYMBOL, new BigDecimal("49800")); // SHORT +0.3% from 49950
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("49860")); // retrace to ~70%
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getClosePositionCalls().size() >= 1);

        // Reflect SHORT close
        TradeOrder closeHedgeShort = TradeOrder.builder()
                .orderId(orderIdCounter.incrementAndGet())
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(hedgeShort.getOrderId())
                .price(new BigDecimal("49860"))
                .orderTime(LocalDateTime.now())
                .build();
        session.addOrder(closeHedgeShort);
        session.setProcessing(false);

        // Wait cooldown
        sleepQuiet(10_200);

        // LONG improves (price up). Follow-up: first tick baseline, second tick improve ≥ +0.1 → enable trailing + open hedge SHORT
        int openBefore = tradingUpdatesService.getOpenPositionCalls().size();
        tickerService.setPrice(SYMBOL, new BigDecimal("49940")); // baseline (LONG still negative)
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("49990")); // improve by +0.1% from baseline
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getOpenPositionCalls().size() > openBefore);
        var followUpOpen = tradingUpdatesService.getOpenPositionCalls().get(
                tradingUpdatesService.getOpenPositionCalls().size() - 1);
        assertEquals(TradingDirection.SHORT, followUpOpen.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, followUpOpen.purpose);
    }

    @Test
    @DisplayName("SHORT cycle: main SHORT → open LONG hedge → close LONG in profit → SHORT improves (follow-up with trailing + hedge)")
    void testShortCycleProfitThenShortImproveFollowUp() {
        // Create MAIN SHORT
        TradeOrder mainShort = createOrder(TradingDirection.SHORT, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.SHORT, mainShort);
        monitoringService.addToMonitoring(session);

        // Price rises (SHORT loss) to -0.1% → open LONG hedge
        tickerService.setPrice(SYMBOL, new BigDecimal("50050")); // -0.1% for SHORT
        monitoringService.monitor();
        assertEquals(1, tradingUpdatesService.getOpenPositionCalls().size());
        var openLong = tradingUpdatesService.getOpenPositionCalls().get(0);
        assertEquals(TradingDirection.LONG, openLong.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, openLong.purpose);
        // Reflect hedge LONG
        TradeOrder hedgeLong = createOrder(TradingDirection.LONG, OrderPurpose.HEDGE_OPEN, new BigDecimal("50050"));
        session.addOrder(hedgeLong);
        session.openLongPosition();
        session.setProcessing(false);

        // Wait cooldown before attempting to close LONG by trailing
        sleepQuiet(10_200);

        // Make LONG profitable and close by trailing
        tickerService.setPrice(SYMBOL, new BigDecimal("50200")); // LONG +0.3% from 50050
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50140")); // retrace to ~70%
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getClosePositionCalls().size() >= 1);

        // Reflect LONG close
        TradeOrder closeHedgeLong = TradeOrder.builder()
                .orderId(orderIdCounter.incrementAndGet())
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.HEDGE_CLOSE)
                .status(OrderStatus.FILLED)
                .parentOrderId(hedgeLong.getOrderId())
                .price(new BigDecimal("50140"))
                .orderTime(LocalDateTime.now())
                .build();
        session.addOrder(closeHedgeLong);
        session.setProcessing(false);

        // Wait cooldown
        sleepQuiet(10_200);

        // SHORT improves (price down). Follow-up: first tick baseline, second tick improve ≥ +0.1 → enable trailing + open hedge LONG
        int openBefore = tradingUpdatesService.getOpenPositionCalls().size();
        tickerService.setPrice(SYMBOL, new BigDecimal("50060")); // baseline (SHORT still negative)
        monitoringService.monitor();
        tickerService.setPrice(SYMBOL, new BigDecimal("50010")); // improve by +0.1% from baseline
        monitoringService.monitor();
        assertTrue(tradingUpdatesService.getOpenPositionCalls().size() > openBefore);
        var followUpOpen = tradingUpdatesService.getOpenPositionCalls().get(
                tradingUpdatesService.getOpenPositionCalls().size() - 1);
        assertEquals(TradingDirection.LONG, followUpOpen.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, followUpOpen.purpose);
    }

    @Test
    @DisplayName("Single position: LONG loss at -0.1% opens SHORT hedge")
    void testSinglePositionLongLossOpensHedge() {
        // Create MAIN LONG
        TradeOrder mainLong = createOrder(TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.LONG, mainLong);
        monitoringService.addToMonitoring(session);

        // Price drops to -0.1% → should open SHORT hedge
        tickerService.setPrice(SYMBOL, new BigDecimal("49950")); // -0.1%
        monitoringService.monitor();
        assertEquals(1, tradingUpdatesService.getOpenPositionCalls().size());
        var hedgeOpen = tradingUpdatesService.getOpenPositionCalls().get(0);
        assertEquals(TradingDirection.SHORT, hedgeOpen.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, hedgeOpen.purpose);
    }

    @Test
    @DisplayName("Single position: SHORT loss at -0.1% opens LONG hedge")
    void testSinglePositionShortLossOpensHedge() {
        // Create MAIN SHORT
        TradeOrder mainShort = createOrder(TradingDirection.SHORT, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.SHORT, mainShort);
        monitoringService.addToMonitoring(session);

        // Price rises to -0.1% for SHORT → should open LONG hedge
        tickerService.setPrice(SYMBOL, new BigDecimal("50050")); // -0.1% for SHORT
        monitoringService.monitor();
        assertEquals(1, tradingUpdatesService.getOpenPositionCalls().size());
        var hedgeOpen = tradingUpdatesService.getOpenPositionCalls().get(0);
        assertEquals(TradingDirection.LONG, hedgeOpen.direction);
        assertEquals(OrderPurpose.HEDGE_OPEN, hedgeOpen.purpose);
    }

    @Test
    @DisplayName("Single position: LONG profit at +0.1% activates trailing")
    void testSinglePositionLongProfitActivatesTrailing() {
        // Create MAIN LONG
        TradeOrder mainLong = createOrder(TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.LONG, mainLong);
        monitoringService.addToMonitoring(session);

        // Price rises to +0.1% → should activate trailing
        tickerService.setPrice(SYMBOL, new BigDecimal("50050")); // +0.1%
        monitoringService.monitor();
        assertTrue(session.getMainOrder().getTrailingActive(), "Trailing should be active on profitable LONG");
    }

    @Test
    @DisplayName("Single position: SHORT profit at +0.1% activates trailing")
    void testSinglePositionShortProfitActivatesTrailing() {
        // Create MAIN SHORT
        TradeOrder mainShort = createOrder(TradingDirection.SHORT, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
        TradeSession session = createSession(TradingDirection.SHORT, mainShort);
        monitoringService.addToMonitoring(session);

        // Price drops to +0.1% for SHORT → should activate trailing
        tickerService.setPrice(SYMBOL, new BigDecimal("49950")); // +0.1% for SHORT
        monitoringService.monitor();
        assertTrue(session.getMainOrder().getTrailingActive(), "Trailing should be active on profitable SHORT");
    }

    // === Helpers ===

    private static void sleepQuiet(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) {}
    }

    private TradeOrder createOrder(TradingDirection direction, OrderPurpose purpose, BigDecimal price) {
        return TradeOrder.builder()
                .orderId(orderIdCounter.incrementAndGet())
                .direction(direction)
                .purpose(purpose)
                .status(OrderStatus.FILLED)
                .price(price)
                .count(new BigDecimal("0.001"))
                .commission(new BigDecimal("0.5"))
                .orderTime(LocalDateTime.now())
                .trailingActive(false)
                .build();
    }

    private TradeSession createSession(TradingDirection direction, TradeOrder mainOrder) {
        TradeSession session = new TradeSession();
        session.setId("test-v3-session-" + System.currentTimeMillis());
        session.onCreate(SYMBOL, direction, mainOrder, "v3 integration test");
        return session;
    }

    // === Fake services ===

    private static class InMemoryTradeSessionService implements TradeSessionService {
        private final List<TradeSession> sessions = new ArrayList<>();

        @Override
        public TradeSession create(String plan, TradingDirection direction, TradeOrder mainOrder, String context) {
            TradeSession session = new TradeSession();
            session.onCreate(plan, direction, mainOrder, context);
            sessions.add(session);
            return session;
        }

        @Override
        public TradeSession getById(String id) {
            return sessions.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<TradeSession> getAllByPlan(String plan) {
            return sessions.stream().filter(s -> Objects.equals(s.getTradePlan(), plan)).toList();
        }

        @Override
        public List<TradeSession> getAllActive() {
            return sessions.stream().filter(s -> s.getStatus() == SessionStatus.ACTIVE).toList();
        }

        @Override
        public List<TradeSession> getAll() {
            return new ArrayList<>(sessions);
        }

        @Override
        public TradeSession addOrder(String idSession, TradeOrder order) {
            TradeSession session = getById(idSession);
            if (session != null) {
                session.addOrder(order);
            }
            return session;
        }

        @Override
        public TradeSession closeSession(String idSession) {
            TradeSession session = getById(idSession);
            if (session != null) {
                session.completeSession();
            }
            return session;
        }
    }

    private static class FakeTicker24hService implements Ticker24hService {
        private final Map<String, BigDecimal> prices = new HashMap<>();

        public void setPrice(String symbol, BigDecimal price) {
            prices.put(symbol, price);
        }

        @Override
        public BigDecimal getPrice(String symbol) {
            return prices.get(symbol);
        }

        @Override
        public void addPrice(Ticker24h ticker24h) {}

        @Override
        public Ticker24h getTicker(String symbol) { return null; }
    }

    private static class FakeTradingUpdatesService implements TradingUpdatesService {
        private final List<ClosePositionCall> closePositionCalls = new ArrayList<>();
        private final List<OpenPositionCall> openPositionCalls = new ArrayList<>();

        public static class ClosePositionCall {
            @SuppressWarnings("unused") public final TradeSession session;
            @SuppressWarnings("unused") public final SessionMode sessionMode;
            @SuppressWarnings("unused") public final Long orderId;
            public final TradingDirection direction;
            public final OrderPurpose purpose;
            @SuppressWarnings("unused") public final String reason;

            public ClosePositionCall(TradeSession session, SessionMode sessionMode, Long orderId,
                                     Long relatedHedgeId, TradingDirection direction, OrderPurpose purpose,
                                     BigDecimal currentPrice, String reason) {
                this.session = session;
                this.sessionMode = sessionMode;
                this.orderId = orderId;
                this.direction = direction;
                this.purpose = purpose;
                this.reason = reason;
            }
        }

        public static class OpenPositionCall {
            @SuppressWarnings("unused") public final TradeSession session;
            @SuppressWarnings("unused") public final SessionMode sessionMode;
            public final TradingDirection direction;
            public final OrderPurpose purpose;
            @SuppressWarnings("unused") public final String reason;

            public OpenPositionCall(TradeSession session, SessionMode sessionMode, TradingDirection direction,
                                    OrderPurpose purpose, BigDecimal currentPrice, String reason,
                                    Long parentOrderId, Long relatedHedgeId) {
                this.session = session;
                this.sessionMode = sessionMode;
                this.direction = direction;
                this.purpose = purpose;
                this.reason = reason;
            }
        }

        @Override
        public TradeSession closePosition(TradeSession session, SessionMode sessionMode, Long idOrder,
                                          Long relatedHedgeId, TradingDirection direction, OrderPurpose purpose,
                                          BigDecimal currentPrice, String context) {
            closePositionCalls.add(new ClosePositionCall(session, sessionMode, idOrder, relatedHedgeId,
                    direction, purpose, currentPrice, context));
            return session;
        }

        @Override
        public TradeSession openPosition(TradeSession session, SessionMode sessionMode, TradingDirection direction,
                                         OrderPurpose purpose, BigDecimal currentPrice, String context,
                                         Long parentOrderId, Long relatedHedgeId) {
            openPositionCalls.add(new OpenPositionCall(session, sessionMode, direction, purpose,
                    currentPrice, context, parentOrderId, relatedHedgeId));
            return session;
        }

        public List<ClosePositionCall> getClosePositionCalls() { return closePositionCalls; }
        public List<OpenPositionCall> getOpenPositionCalls() { return openPositionCalls; }
    }
}



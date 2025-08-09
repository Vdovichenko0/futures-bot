package io.cryptobot.binance.trading.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.mapper.OrderMapper;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trading.updates.TradingUpdatesServiceImpl;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringServiceImplIntegrationTest {

    private static final String SYMBOL = "COINUSDT";
    
    // === КОНСТАНТЫ ТЕСТИРОВАНИЯ ===
    // Копируем константы из MonitoringServiceImpl для тестирования
    private static final BigDecimal TRAILING_ACTIVATION_THRESHOLD = BigDecimal.valueOf(0.15); // Активация трейлинга 0.15%
    private static final BigDecimal TRAILING_RETRACE_RATIO = BigDecimal.valueOf(0.8);         // Откат трейлинга 80%
    private static final BigDecimal TRACKING_ACTIVATION_THRESHOLD = BigDecimal.valueOf(-0.3); // Активация отслеживания -0.3%
    private static final BigDecimal WORSENING_THRESHOLD = BigDecimal.valueOf(-0.1);           // Порог ухудшения -0.1%
    private static final BigDecimal IMPROVEMENT_THRESHOLD = BigDecimal.valueOf(0.1);          // Порог улучшения 0.1%
    private static final BigDecimal PULLBACK_RATIO = BigDecimal.valueOf(0.7);                 // Соотношение отката 70%
    private static final long TEST_COOLDOWN_MS = 11_000;                                      // Кулдаун для тестов 11 сек (больше ORDER_COOLDOWN_MS)

    private InMemoryTradeSessionService sessionService;
    private FakeTicker24hService tickerService;
    private FakeOrderService orderService;
    private FakeTradePlanGetService planGetService;

    private TradingUpdatesServiceImpl updatesService;
    private MonitoringServiceImpl monitoring;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sessionService = new InMemoryTradeSessionService();
        tickerService = new FakeTicker24hService();
        orderService = new FakeOrderService();
        planGetService = new FakeTradePlanGetService(
                TradePlan.builder()
                        .symbol(SYMBOL)
                        .amountPerTrade(BigDecimal.valueOf(1000))
                        .leverage(10)
                        .sizes(SizeModel.builder().lotSize(BigDecimal.ONE).build())
                        .build()
        );

        updatesService = new TradingUpdatesServiceImpl(planGetService, sessionService, orderService);
        monitoring = new MonitoringServiceImpl(sessionService, tickerService, updatesService);
    }

    @Test
    void fullCycle_longMain_openShort_closeMain_openLong_closeLong_closeShort_completeSession() throws Exception {
        // 1) MAIN LONG via WS mapping
        Order mainFilled = wsFilledOrder(100L, SYMBOL, OrderSide.BUY, new BigDecimal("100.00"));
        TradeOrder mainOrder = toTradeOrder(mainFilled, SessionMode.SCALPING, "main_entry", TradingDirection.LONG, OrderPurpose.MAIN_OPEN);
        TradeSession session = sessionService.create(SYMBOL, TradingDirection.LONG, mainOrder, "entry");
        monitoring.addToMonitoring(session);

        // 2) Price down: open SHORT hedge
        tickerService.setPrice(new BigDecimal("99.60"));
        monitoring.monitor(); // start tracking
        tickerService.setPrice(new BigDecimal("99.45"));
        monitoring.monitor(); // open SHORT hedge
        waitCooldown();

        TradeSession afterHedgeShort = sessionService.getById(session.getId());
        assertTrue(afterHedgeShort.hasBothPositionsActive());
        Long hedgeShortId = afterHedgeShort.getLastHedgeOrder().getOrderId();
        assertNotNull(hedgeShortId);

        // 3) Price up and pullback: close MAIN (best) by trailing
        tickerService.setPrice(new BigDecimal("100.30"));
        monitoring.monitor(); // activate trailing on MAIN (best)
        tickerService.setPrice(new BigDecimal("100.20"));
        monitoring.monitor(); // retrace → close MAIN
        waitCooldown();

        TradeSession afterMainClose = sessionService.getById(session.getId());
        assertFalse(afterMainClose.isActiveLong(), "MAIN LONG should be closed");
        assertTrue(afterMainClose.isActiveShort(), "SHORT hedge should remain active");

        // 4) Further up: single SHORT worsens → open LONG hedge (re-hedge)
        tickerService.setPrice(new BigDecimal("100.50"));
        monitoring.monitor(); // base tracking for single short
        tickerService.setPrice(new BigDecimal("100.80"));
        monitoring.monitor(); // open LONG hedge
        waitCooldown();

        TradeSession afterLongHedge = sessionService.getById(session.getId());
        assertTrue(afterLongHedge.hasBothPositionsActive(), "Both positions should be active after re-hedge");

        // 5) Slight up then pullback: close best (likely LONG hedge) by trailing
        tickerService.setPrice(new BigDecimal("101.00"));
        monitoring.monitor(); // activate trailing on best (LONG)
        tickerService.setPrice(new BigDecimal("100.80"));
        monitoring.monitor(); // close best (LONG hedge)
        waitCooldown();

        TradeSession afterCloseLongHedge = sessionService.getById(session.getId());
        assertFalse(afterCloseLongHedge.isActiveLong(), "LONG should be closed");
        assertTrue(afterCloseLongHedge.isActiveShort(), "SHORT should remain active");

        // 6) Strong drop and slight bounce: close remaining SHORT by trailing
        tickerService.setPrice(new BigDecimal("99.00"));
        monitoring.monitor(); // activate trailing on single SHORT (positive pnl)
        tickerService.setPrice(new BigDecimal("99.20"));
        monitoring.monitor(); // retrace → close SHORT
        monitoring.monitor();
        waitCooldown();

        TradeSession afterAllClosed = sessionService.getById(session.getId());
        assertFalse(afterAllClosed.hasActivePosition());
        assertEquals(SessionStatus.COMPLETED, afterAllClosed.getStatus());
    }

    @Test
    void cycle1_mainShort_then_closeShort_only() throws Exception {
        // MAIN SHORT via WS mapping
        Order mainShort = wsFilledOrder(210L, SYMBOL, OrderSide.SELL, new BigDecimal("100.00"));
        TradeOrder mainOrder = toTradeOrder(mainShort, SessionMode.SCALPING, "main_entry_short", TradingDirection.SHORT, OrderPurpose.MAIN_OPEN);
        TradeSession session = sessionService.create(SYMBOL, TradingDirection.SHORT, mainOrder, "entry");
        monitoring.addToMonitoring(session);

        // Price down to activate trailing (SHORT gains when price < entry)
        tickerService.setPrice(new BigDecimal("99.70")); // +0.30%
        monitoring.monitor();
        // Retrace to trigger close (<=80% of high; 0.16% <= 0.24%)
        tickerService.setPrice(new BigDecimal("99.84"));
        monitoring.monitor();
        waitCooldown();

        TradeSession after = sessionService.getById(session.getId());
        assertFalse(after.hasActivePosition());
        assertEquals(SessionStatus.COMPLETED, after.getStatus());
    }

    @Test
    void cycle2_mainShort_openLong_closeLong_closeShort_sessionClosed() throws Exception {
        Order mainShort = wsFilledOrder(220L, SYMBOL, OrderSide.SELL, new BigDecimal("100.00"));
        TradeOrder mainOrder = toTradeOrder(mainShort, SessionMode.SCALPING, "main_entry_short2", TradingDirection.SHORT, OrderPurpose.MAIN_OPEN);
        TradeSession session = sessionService.create(SYMBOL, TradingDirection.SHORT, mainOrder, "entry");
        monitoring.addToMonitoring(session);

        // Open hedge LONG on worsening
        tickerService.setPrice(new BigDecimal("100.30"));
        monitoring.monitor(); // base
        tickerService.setPrice(new BigDecimal("100.41"));
        monitoring.monitor(); // open LONG hedge
        waitCooldown();

        // Close best (LONG) by trailing
        tickerService.setPrice(new BigDecimal("100.80"));
        monitoring.monitor(); // activate trailing on LONG
        tickerService.setPrice(new BigDecimal("100.60"));
        monitoring.monitor(); // retrace -> close LONG
        waitCooldown();

        // Close remaining SHORT by trailing
        tickerService.setPrice(new BigDecimal("99.00"));
        monitoring.monitor(); // activate trailing on SHORT
        tickerService.setPrice(new BigDecimal("99.20"));
        monitoring.monitor(); // retrace -> close SHORT
        waitCooldown();

        TradeSession after = sessionService.getById(session.getId());
        assertFalse(after.hasActivePosition());
        assertEquals(SessionStatus.COMPLETED, after.getStatus());
    }

    @Test
    void cycle3_mainShort_openLong_closeMain_openShortHedge_closeShortHedge_closeLong_sessionClosed() throws Exception {
        Order mainShort = wsFilledOrder(230L, SYMBOL, OrderSide.SELL, new BigDecimal("100.00"));
        TradeOrder mainOrder = toTradeOrder(mainShort, SessionMode.SCALPING, "main_entry_short3", TradingDirection.SHORT, OrderPurpose.MAIN_OPEN);
        TradeSession session = sessionService.create(SYMBOL, TradingDirection.SHORT, mainOrder, "entry");
        monitoring.addToMonitoring(session);

        // Open hedge LONG on worsening
        tickerService.setPrice(new BigDecimal("100.30"));
        monitoring.monitor();
        tickerService.setPrice(new BigDecimal("100.41"));
        monitoring.monitor(); // open LONG hedge
        waitCooldown();

        // Close MAIN SHORT as best (price down then retrace)
        tickerService.setPrice(new BigDecimal("99.70"));
        monitoring.monitor(); // best SHORT trailing activate
        tickerService.setPrice(new BigDecimal("99.84"));
        monitoring.monitor(); // close MAIN SHORT
        waitCooldown();

        TradeSession afterMainClosed = sessionService.getById(session.getId());
        assertFalse(afterMainClosed.isActiveShort());
        assertTrue(afterMainClosed.isActiveLong());

        // Open SHORT hedge while MAIN is closed (single LONG active): price falls
        tickerService.setPrice(new BigDecimal("100.10"));
        monitoring.monitor(); // base tracking for LONG (negative pnl <= -0.3)
        tickerService.setPrice(new BigDecimal("99.99"));
        monitoring.monitor(); // delta -0.11 -> open SHORT hedge
        waitCooldown();

        // Close SHORT hedge as best (continue down then retrace up a bit)
        tickerService.setPrice(new BigDecimal("99.60"));
        monitoring.monitor(); // activate trailing on SHORT
        tickerService.setPrice(new BigDecimal("99.72"));
        monitoring.monitor(); // close SHORT hedge
        waitCooldown();

        // Close remaining LONG by trailing (go up then retrace)
        tickerService.setPrice(new BigDecimal("100.60"));
        monitoring.monitor(); // activate trailing on LONG
        tickerService.setPrice(new BigDecimal("100.45"));
        monitoring.monitor(); // close LONG
        waitCooldown();

        TradeSession after = sessionService.getById(session.getId());
        assertFalse(after.hasActivePosition());
        assertEquals(SessionStatus.COMPLETED, after.getStatus());
    }

    // ---------- Helpers ----------

    private TradeOrder toTradeOrder(Order order, SessionMode mode, String ctx, TradingDirection dir, OrderPurpose purpose) {
        TradePlan plan = planGetService.getPlan(order.getSymbol());
        TradeOrder to = new TradeOrder();
        to.onCreate(order, BigDecimal.ZERO, mode, ctx, plan, dir, purpose, null, null);
        to.setTrailingActive(false);
        to.setPnlHigh(BigDecimal.ZERO);
        to.setBasePnl(null);
        to.setMaxChangePnl(null);
        return to;
    }

    private Order wsFilledOrder(long id, String symbol, OrderSide side, BigDecimal avgPrice) throws Exception {
        ObjectNode n = mapper.createObjectNode();
        n.put("i", id);
        n.put("s", symbol);
        n.put("S", side.name());
        n.put("o", "MARKET");
        n.put("X", OrderStatus.FILLED.name());
        n.put("ap", avgPrice.toPlainString());
        n.put("q", "1");
        n.put("L", avgPrice.toPlainString());
        n.put("l", "1");
        n.put("z", "1");
        n.put("T", System.currentTimeMillis());
        JsonNode node = mapper.readTree(n.toString());
        return OrderMapper.fromWS(node);
    }

    private static void waitCooldown() {
        try {
            Thread.sleep(TEST_COOLDOWN_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- Fakes ----------

    private static class InMemoryTradeSessionService implements TradeSessionService {
        private final Map<String, TradeSession> store = new HashMap<>();
        private long seq = 1;

        @Override
        public TradeSession create(String plan, TradingDirection direction, TradeOrder mainOrder, String context) {
            TradeSession s = TradeSession.builder()
                    .id("sess-" + (seq++))
                    .tradePlan(plan)
                    .orders(new ArrayList<>())
                    .build();
            s.onCreate(plan, direction, mainOrder, context);
            store.put(s.getId(), s);
            return s;
        }

        @Override
        public TradeSession getById(String idPlan) { return store.get(idPlan); }
        @Override
        public List<TradeSession> getAllByPlan(String plan) { return List.copyOf(store.values()); }
        @Override
        public List<TradeSession> getAllActive() { return store.values().stream().filter(s -> s.getStatus()== SessionStatus.ACTIVE).toList(); }
        @Override
        public List<TradeSession> getAll() { return List.copyOf(store.values()); }
        @Override
        public TradeSession addOrder(String idSession, TradeOrder order) {
            TradeSession s = getById(idSession);
            s.addOrder(order);
            s.setProcessing(false);
            store.put(idSession, s);
            return s;
        }
        @Override
        public TradeSession setMode(String idSession, io.cryptobot.binance.trade.session.enums.SessionMode newMode) { return getById(idSession); }
        @Override
        public TradeSession closeSession(String idSession) {
            TradeSession s = getById(idSession);
            s.completeSession();
            store.put(idSession, s);
            return s;
        }
    }

    private static class FakeTradePlanGetService implements TradePlanGetService {
        private final TradePlan plan;
        FakeTradePlanGetService(TradePlan plan) { this.plan = plan; }
        @Override public TradePlan getPlan(String symbol) { return plan; }
        @Override public List<TradePlan> getAll() { return List.of(plan); }
        @Override public List<TradePlan> getAllActiveTrue() { return List.of(plan); }
        @Override public List<TradePlan> getAllActiveFalse() { return List.of(plan); }
    }

    private static class FakeTicker24hService implements Ticker24hService {
        private BigDecimal price = BigDecimal.ZERO;
        public void setPrice(BigDecimal p) { this.price = p; }
        @Override public void addPrice(io.cryptobot.market_data.ticker24h.Ticker24h ticker24h) { }
        @Override public BigDecimal getPrice(String coin) { return price; }
        @Override public io.cryptobot.market_data.ticker24h.Ticker24h getTicker(String coin) { return null; }
    }

    private class FakeOrderService implements OrderService {
        private final Map<Long, Order> orders = new HashMap<>();
        private final AtomicLong idGen = new AtomicLong(1000);

        @Override
        public void updateOrder(Order updatedOrder) {
            if (updatedOrder == null || updatedOrder.getOrderId() == null) return;
            Order cur = orders.get(updatedOrder.getOrderId());
            if (cur == null) {
                orders.put(updatedOrder.getOrderId(), updatedOrder);
                return;
            }
            if (updatedOrder.getOrderStatus() != null) cur.setOrderStatus(updatedOrder.getOrderStatus());
            if (updatedOrder.getAveragePrice() != null) cur.setAveragePrice(updatedOrder.getAveragePrice());
            if (updatedOrder.getCommission() != null) cur.setCommission(updatedOrder.getCommission());
            if (updatedOrder.getCommissionAsset() != null) cur.setCommissionAsset(updatedOrder.getCommissionAsset());
            if (updatedOrder.getQuantity() != null) cur.setQuantity(updatedOrder.getQuantity());
            if (updatedOrder.getPrice() != null) cur.setPrice(updatedOrder.getPrice());
            if (updatedOrder.getTradeTime() != 0) cur.setTradeTime(updatedOrder.getTradeTime());
        }

        @Override
        public Order createOrder(String symbol, Double amount, OrderSide side, Boolean hedgeMode) {
            long id = idGen.incrementAndGet();
            Order o = Order.builder()
                    .orderId(id)
                    .symbol(symbol)
                    .side(side)
                    .orderType("MARKET")
                    .quantity(BigDecimal.valueOf(amount))
                    .averagePrice(null)
                    .orderStatus(OrderStatus.NEW)
                    .tradeTime(System.currentTimeMillis())
                    .build();
            orders.put(id, o);
            return o;
        }

        @Override
        public Order closeOrder(Order order) { return createOrder(order.getSymbol(), order.getQuantity().doubleValue(), order.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY, true); }
        @Override
        public Order closeOrder(TradeOrder order) { return createOrder(order.getSymbol(), order.getCount().doubleValue(), order.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY, true); }

        @Override
        public Order getOrder(Long idOrder) {
            Order cur = orders.get(idOrder);
            if (cur == null) return null;
            if (cur.getOrderStatus() != OrderStatus.FILLED) {
                BigDecimal px = tickerService.getPrice(cur.getSymbol());
                ObjectNode n = new ObjectMapper().createObjectNode();
                n.put("i", cur.getOrderId());
                n.put("s", cur.getSymbol());
                n.put("S", cur.getSide().name());
                n.put("o", cur.getOrderType());
                n.put("X", OrderStatus.FILLED.name());
                n.put("ap", px != null ? px.toPlainString() : "0");
                n.put("q", cur.getQuantity() != null ? cur.getQuantity().toPlainString() : "1");
                n.put("L", px != null ? px.toPlainString() : "0");
                n.put("l", cur.getQuantity() != null ? cur.getQuantity().toPlainString() : "1");
                n.put("z", cur.getQuantity() != null ? cur.getQuantity().toPlainString() : "1");
                n.put("T", System.currentTimeMillis());
                Order filled = OrderMapper.fromWS(n);
                updateOrder(filled);
            }
            return orders.get(idOrder);
        }

        @Override
        public Order getOrderFromBinance(Long idOrder, String symbol) { return getOrder(idOrder); }
    }
}
package io.cryptobot.binance.trading.monitoring.v3;

import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import io.cryptobot.binance.trading.monitoring.v3.models.FollowUpState;
import io.cryptobot.binance.trading.monitoring.v3.models.SingleTrackState;
import io.cryptobot.binance.trading.monitoring.v3.utils.CheckAveraging;
import io.cryptobot.binance.trading.monitoring.v3.utils.CheckTrailing;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.configs.locks.TradeSessionLockRegistry;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.order.enums.OrderPurpose;

/**
 * MonitoringServiceV3Impl — стратегия «максимальный цикл».
 * 1) Одна позиция: трейл по прибыли, отслеживание при просадке, ранний хедж ДО трекинга.
 * 2) Две позиции: трейл у best (закрываем best), запускаем follow-up у worst.
 * 3) Follow-up одной позиции: worsen/improve → (ре-)хедж, либо финальное закрытие по трейлу.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringServiceV3Impl implements MonitoringServiceV3 {
    private final MonitorHelper monitorHelper;
    private final CheckAveraging averaging;

    // === КОНСТАНТЫ === //todo add to config + api update
    private static final long MONITORING_INTERVAL_MS = 1_000;      // Интервал мониторинга
    private static final long ORDER_COOLDOWN_MS = 10_000;          // Кулдаун между ордерами

    // PnL в %
    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);

    // Комиссия (в проц.пунктах), учитываем при трейлинге
    private static final BigDecimal COMMISSION_PCT = new BigDecimal("0.036"); // 0.036%

    // Трейлинг
//    private static final BigDecimal TRAILING_ACTIVATION_THRESHOLD_PCT = new BigDecimal("0.10"); // Активация при +0.10%
    private static final BigDecimal TRAILING_CLOSE_RETRACE_RATIO = new BigDecimal("0.80");  // Закрытие при 20% отката

    // Одна позиция: отслеживание и ранний хедж
    private static final BigDecimal SINGLE_TRACKING_START_PCT = new BigDecimal("-0.20"); // Старт отслеживания при -0.20%
    private static final BigDecimal SINGLE_EARLY_HEDGE_PCT = new BigDecimal("-0.10"); // Ранний хедж до трекинга
    private static final BigDecimal SINGLE_WORSEN_DELTA_PCT = new BigDecimal("-0.06"); // Ухудшение от baseline → хедж
    private static final BigDecimal SINGLE_IMPROVE_DELTA_PCT = new BigDecimal("0.05");  // Улучшение > +0.10% → ждём откат ≥30% и хедж (old)

    // Две позиции
    private static final BigDecimal TWO_POS_PROFITABLE_ACTIVATION_PCT = new BigDecimal("0.20");  // Порог активации трейла у best

    // Follow-up после закрытия best (сохраняем ссылочную прибыль пары)
    //private static final BigDecimal FOLLOW_UP_ONE_THIRD_PROFIT_RATIO = new BigDecimal("0.3333"); // при необходимости

    // === СЕРВИСЫ И СОСТОЯНИЕ ===
    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;
    private final TradingUpdatesService tradingUpdatesService;
    private final CheckTrailing checkTrailing;

    private final ConcurrentHashMap<String, TradeSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastOrderAtMsBySession = new ConcurrentHashMap<>();

    // Отслеживание одной позиции ДО хеджа: baseline и локальный "soft trailing" для условия 2.2(b)
    private final ConcurrentHashMap<String, SingleTrackState> singleTrackBySession = new ConcurrentHashMap<>();

    // Follow-up у оставшейся ноги ПОСЛЕ закрытия best в двух позициях
    private final ConcurrentHashMap<String, FollowUpState> followUpBySession = new ConcurrentHashMap<>();
    private final ExecutorService monitorPool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    private final TradeSessionLockRegistry lockRegistry;
//    private final ConcurrentHashMap<String, Boolean> openInFlightByDir = new ConcurrentHashMap<>();

    @PreDestroy
    public void shutdownPool() {
        monitorPool.shutdown();
    }

    @PostConstruct
    public void init() {
        for (TradeSession s : sessionService.getAllActive()) {
            sessions.put(s.getId(), s);
        }
    }

    @Override
    public void addToMonitoring(TradeSession tradeSession) {
        sessions.put(tradeSession.getId(), tradeSession);
    }

    @Override
    public void removeFromMonitoring(String idSession) { //todo check runtime + many orders
        sessions.remove(idSession);
        singleTrackBySession.remove(idSession);
        lastOrderAtMsBySession.remove(idSession);
    }

    @Scheduled(fixedRate = MONITORING_INTERVAL_MS)
    public void monitor() {
        // берём снимок, чтобы итерация была стабильной
        List<TradeSession> snapshot = new ArrayList<>(sessions.values());
        for (TradeSession session : snapshot) {
            monitorPool.submit(() -> {
                try {
                    monitorSession(session);
                } catch (Exception e) {
                    log.error("❌ monitor error {}: {}", session.getId(), e.getMessage(), e);
                }
            });
        }
    }

    private void monitorSession(TradeSession session) {
        if (!sessions.containsKey(session.getId())) {
            return;
        }

        ReentrantLock lock = lockRegistry.getLock(session.getId());
        if (!lock.tryLock()) {
            log.debug("🔒 {} skip monitor: session lock busy", session.getId());
            return;
        }
        try {
            if (session.isProcessing()) return;
            if (session.getStatus().equals(SessionStatus.COMPLETED)) {
                removeFromMonitoring(session.getId());
                return;
            }

            BigDecimal price = ticker24hService.getPrice(session.getTradePlan());
            if (price == null) return;

            boolean bothActive = session.hasBothPositionsActive();
            boolean anyActive = session.hasActivePosition();

            if (bothActive) {
                applyTwoPositionsLogic(session, price);
                return;
            }
            if (anyActive) {
                //get direction by one opened position
                TradingDirection dir = session.isActiveLong() ? TradingDirection.LONG : session.isActiveShort() ? TradingDirection.SHORT : null;

                TradeOrder active = monitorHelper.getLatestActiveOrderByDirection(session,dir);
                if (active == null || active.getPrice() == null || active.getPrice().compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("⚠️ Session {}: No active order found for monitoring, skipping", session.getId());
                    return;
                }
                BigDecimal pnl = calcPnl(active, price);

                applySinglePositionLogic(session, price, active, pnl);
            }
        } finally {
            lock.unlock();
        }
    }

    // --- SINGLE POSITION ---
    private void applySinglePositionLogic(TradeSession session, BigDecimal price, TradeOrder order, BigDecimal pnl) {
        // если уже есть follow-up (после двух ног) — работаем строго с убыточной ногой
        FollowUpState fu = followUpBySession.get(session.getId());
        if (fu != null) {
            handleFollowUpSingle(session, price, fu);
            return;
        }

        // обычный трейлинг в плюс → закрываем
        if (checkTrailing.checkNewTrailing(order, pnl)) {
            log.info("💰 {} [{}] CLOSING {} position: PnL={}%, high={}%, entry={}, current={}",
                    session.getId(),
                    session.getTradePlan(),
                    order.getDirection(),
                    pnl.setScale(3, RoundingMode.HALF_UP),
                    monitorHelper.nvl(order.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
                    order.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));
//            executeClosePosition(session, order, SessionMode.SCALPING,
//                    String.format("single_trailing high=%.3f retrace<=%.3f",
//                            monitorHelper.nvl(order.getPnlHigh()),
//                            monitorHelper.nvl(order.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
            routeClose(session, order, SessionMode.SCALPING,
                    String.format("single_trailing high=%.3f retrace<=%.3f",
                            monitorHelper.nvl(order.getPnlHigh()),
                            monitorHelper.nvl(order.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));

            singleTrackBySession.remove(session.getId());
            return;
        }

        // ранний хедж до начала трекинга
        SingleTrackState st = singleTrackBySession.get(session.getId());
        if (st == null && pnl.compareTo(SINGLE_EARLY_HEDGE_PCT) <= 0) {
            TradingDirection hedgeDir = monitorHelper.opposite(order.getDirection());
            log.info("⚠️ {} [{}] EARLY HEDGE {}: pnl={}% <= {}% (before tracking), entry={}, current={}",
                    session.getId(), 
                    session.getTradePlan(),
                    hedgeDir,
                    pnl.setScale(3, RoundingMode.HALF_UP), 
                    SINGLE_EARLY_HEDGE_PCT,
                    order.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));
            executeOpenHedge(session, hedgeDir, "HEDGE_OPEN", price,
                    String.format("early_hedge pnl<=%.3f before_tracking", SINGLE_EARLY_HEDGE_PCT));
            return;
        }

        // старт трекинга при -0.20%
        if (st == null && pnl.compareTo(SINGLE_TRACKING_START_PCT) <= 0) {
            st = SingleTrackState.builder()
                    .baseline(pnl)
                    .tracking(true)
                    .trailActive(false)
                    .trailHigh(pnl) // от этого уровня начнём улучшение
                    .build();
            singleTrackBySession.put(session.getId(), st);
            log.info("🧭 {} [{}] SINGLE TRACKING START {}: baseline={}%", 
                    session.getId(), 
                    session.getTradePlan(),
                    order.getDirection(),
                    st.getBaseline());
            return;
        }

        // если трекинг активен — работаем по дельтам к baseline
        if (st != null && st.isTracking()) {
            BigDecimal delta = pnl.subtract(st.getBaseline());

            // ухудшение -0.10 → хедж
            if (delta.compareTo(SINGLE_WORSEN_DELTA_PCT) <= 0) {
                TradingDirection hedgeDir = monitorHelper.opposite(order.getDirection());
                log.info("📉 {} [{}] SINGLE TRACKING WORSEN {}: delta={}% <= {}% (baseline={}%), current PnL={}%, entry={}, current={}",
                        session.getId(), 
                        session.getTradePlan(),
                        hedgeDir,
                        delta.setScale(3, RoundingMode.HALF_UP),
                        SINGLE_WORSEN_DELTA_PCT, 
                        st.getBaseline().setScale(3, RoundingMode.HALF_UP),
                        pnl.setScale(3, RoundingMode.HALF_UP),
                        order.getPrice().setScale(8, RoundingMode.HALF_UP),
                        price.setScale(8, RoundingMode.HALF_UP));
                executeOpenHedge(session, hedgeDir, "HEDGE_OPEN", price,
                        String.format("single_tracking_worsen delta<=%.3f from %.3f", SINGLE_WORSEN_DELTA_PCT, st.getBaseline()));
                singleTrackBySession.remove(session.getId());
                return;
            }

            // улучшение > +0.10 → включаем «мягкий трейл»: ждём откат ≥80% от high (с учётом комиссии) и ОТКРЫВАЕМ ХЕДЖ
            // FIX: активируем soft-trail только если текущий pnl > 0 (реальная прибыль)
            if (!st.isTrailActive() && delta.compareTo(SINGLE_IMPROVE_DELTA_PCT) > 0 && pnl.compareTo(BigDecimal.ZERO) > 0) {
                st.setTrailActive(true);
                st.setTrailHigh(pnl);
                log.info("🚀 {} [{}] SINGLE SOFT-TRAIL ENABLED {}: delta={}% > {}% (baseline={}%), pnl>0",
                        session.getId(), 
                        session.getTradePlan(),
                        order.getDirection(),
                        delta.setScale(3, RoundingMode.HALF_UP),
                        SINGLE_IMPROVE_DELTA_PCT, 
                        st.getBaseline().setScale(3, RoundingMode.HALF_UP));
            }
            if (st.isTrailActive()) {
                // обновляем локальный high
                if (pnl.compareTo(st.getTrailHigh()) > 0) {
                    BigDecimal oldHigh = st.getTrailHigh();
                    st.setTrailHigh(pnl);
                    log.info("📈 {} [{}] SINGLE SOFT-TRAIL HIGH UPDATED {}: {}% → {}%",
                            session.getId(), 
                            session.getTradePlan(),
                            order.getDirection(),
                            oldHigh.setScale(3, RoundingMode.HALF_UP), 
                            pnl.setScale(3, RoundingMode.HALF_UP));
                }

                // FIX: если high ≤ 0 — не армим откат (ещё нет прибыли для трейла)
                if (st.getTrailHigh().compareTo(BigDecimal.ZERO) <= 0) {
                    return; // ждём, пока выйдем в положительную зону
                }

                BigDecimal softRetrace = st.getTrailHigh().multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT);
                // FIX: не обнуляем softRetrace до 0; если ≤0 — не триггерим
                if (softRetrace.compareTo(BigDecimal.ZERO) > 0 && pnl.compareTo(softRetrace) <= 0) {
                    TradingDirection hedgeDir = monitorHelper.opposite(order.getDirection());
                    log.info("🔴 {} [{}] SINGLE SOFT-TRAIL TRIGGERED {}: current={}% <= retrace={}% (high={}%), entry={}, current={}",
                            session.getId(), 
                            session.getTradePlan(),
                            hedgeDir,
                            pnl.setScale(3, RoundingMode.HALF_UP),
                            softRetrace.setScale(3, RoundingMode.HALF_UP),
                            st.getTrailHigh().setScale(3, RoundingMode.HALF_UP),
                            order.getPrice().setScale(8, RoundingMode.HALF_UP),
                            price.setScale(8, RoundingMode.HALF_UP));
                    executeOpenHedge(session, hedgeDir, "HEDGE_OPEN", price,
                            String.format("single_soft_trail_retrace high=%.3f retrace<=%.3f", st.getTrailHigh(), softRetrace));
                    singleTrackBySession.remove(session.getId());
                    return;
                }
            }
        }
    }

    // follow-up у оставшейся ноги после закрытия best
    private void handleFollowUpSingle(TradeSession session, BigDecimal price, FollowUpState fu) {
        TradeOrder losing = monitorHelper.getLatestActiveOrderByDirection(session, fu.getLosingDirection());
        if (losing == null || losing.getPrice() == null || losing.getPrice().signum() == 0) {
            followUpBySession.remove(session.getId());
            return;
        }
        BigDecimal entry = losing.getPrice();
        BigDecimal pnl = (losing.getDirection() == TradingDirection.LONG)
                ? price.subtract(entry).divide(entry, 8, RoundingMode.HALF_UP).multiply(PERCENTAGE_MULTIPLIER)
                : entry.subtract(price).divide(entry, 8, RoundingMode.HALF_UP).multiply(PERCENTAGE_MULTIPLIER);

        if (!fu.isBaselineFixed()) {
            fu.setBaseline(pnl);
            fu.setBaselineFixed(true);
            fu.setSoftTrailActive(false);
            fu.setSoftTrailHigh(pnl);
            log.info("🧭 {} [{}] FOLLOW-UP START {}: baseline={}%, order={}",
                    session.getId(), 
                    session.getTradePlan(),
                    fu.getLosingDirection(),
                    fu.getBaseline().setScale(3, RoundingMode.HALF_UP),
                    losing.getOrderId());
            return;
        }

        BigDecimal delta = pnl.subtract(fu.getBaseline());

        // (а) ухудшение -0.10 → ре-хедж
        if (delta.compareTo(SINGLE_WORSEN_DELTA_PCT) <= 0) {
            TradingDirection hedgeDir = monitorHelper.opposite(losing.getDirection());
            log.info("📉 {} [{}] FOLLOW-UP WORSEN {}: delta={}% <= {}% (baseline={}%), current PnL={}%, entry={}, current={}",
                    session.getId(), 
                    session.getTradePlan(),
                    hedgeDir,
                    delta.setScale(3, RoundingMode.HALF_UP),
                    SINGLE_WORSEN_DELTA_PCT, 
                    fu.getBaseline().setScale(3, RoundingMode.HALF_UP),
                    pnl.setScale(3, RoundingMode.HALF_UP),
                    losing.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));
            executeOpenHedge(session, monitorHelper.opposite(losing.getDirection()), "HEDGE_OPEN", price,
                    String.format("follow_up_worsen delta<=%.3f from %.3f", SINGLE_WORSEN_DELTA_PCT, fu.getBaseline()));
            followUpBySession.remove(session.getId());
            return;
        }

        // (б) улучшение > +0.10 и затем откат ≥80% от high (учёт комиссии) → ре-хедж
        // FIX: активируем soft-trail только если текущий pnl > 0 (реальная прибыль)
        if (!fu.isSoftTrailActive() && delta.compareTo(SINGLE_IMPROVE_DELTA_PCT) > 0 && pnl.compareTo(BigDecimal.ZERO) > 0) {
            fu.setSoftTrailActive(true);
            fu.setSoftTrailHigh(pnl);
            log.info("🚀 {} [{}] FOLLOW-UP SOFT-TRAIL ENABLED {}: delta={}% > {}% (baseline={}%), pnl>0",
                    session.getId(), 
                    session.getTradePlan(),
                    fu.getLosingDirection(),
                    delta.setScale(3, RoundingMode.HALF_UP),
                    SINGLE_IMPROVE_DELTA_PCT, 
                    fu.getBaseline().setScale(3, RoundingMode.HALF_UP));
        }
        if (fu.isSoftTrailActive()) {
            if (pnl.compareTo(fu.getSoftTrailHigh()) > 0) {
                BigDecimal oldHigh = fu.getSoftTrailHigh();
                fu.setSoftTrailHigh(pnl);
                log.info("📈 {} [{}] FOLLOW-UP SOFT-TRAIL HIGH UPDATED {}: {}% → {}%",
                        session.getId(), 
                        session.getTradePlan(),
                        fu.getLosingDirection(),
                        oldHigh.setScale(3, RoundingMode.HALF_UP), 
                        pnl.setScale(3, RoundingMode.HALF_UP));
            }

            // FIX: если high ≤ 0 — не армим откат (нет профита)
            if (fu.getSoftTrailHigh().compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal softRetrace = fu.getSoftTrailHigh().multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT);
            // FIX: не обнуляем до 0; если ≤0 — не триггерим
            if (softRetrace.compareTo(BigDecimal.ZERO) > 0 && pnl.compareTo(softRetrace) <= 0) {
                TradingDirection hedgeDir = monitorHelper.opposite(losing.getDirection());
                log.info("🔴 {} [{}] FOLLOW-UP SOFT-TRAIL TRIGGERED {}: current={}% <= retrace={}% (high={}%), entry={}, current={}",
                        session.getId(), 
                        session.getTradePlan(),
                        hedgeDir,
                        pnl.setScale(3, RoundingMode.HALF_UP),
                        softRetrace.setScale(3, RoundingMode.HALF_UP),
                        fu.getSoftTrailHigh().setScale(3, RoundingMode.HALF_UP),
                        losing.getPrice().setScale(8, RoundingMode.HALF_UP),
                        price.setScale(8, RoundingMode.HALF_UP));
                executeOpenHedge(session, monitorHelper.opposite(losing.getDirection()), "HEDGE_OPEN", price,
                        String.format("follow_up_soft_trail_retrace high=%.3f retrace<=%.3f", fu.getSoftTrailHigh(), softRetrace));
                followUpBySession.remove(session.getId());
                return;
            }
        }

        // (в) или worst сама уйдёт в плюс и закроется по обычному трейлу
        if (checkTrailing.checkNewTrailing(losing, pnl)) {
            log.info("💰 {} [{}] FOLLOW-UP CLOSING {} position: PnL={}%, high={}%, entry={}, current={}",
                    session.getId(),
                    session.getTradePlan(),
                    losing.getDirection(),
                    pnl.setScale(3, RoundingMode.HALF_UP),
                    monitorHelper.nvl(losing.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
                    losing.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));
//            executeClosePosition(session, losing, SessionMode.SCALPING,
//                    String.format("follow_up_trailing high=%.3f retrace<=%.3f",
//                            monitorHelper.nvl(losing.getPnlHigh()),
//                            monitorHelper.nvl(losing.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
            routeClose(session, losing, SessionMode.SCALPING,
                    String.format("follow_up_trailing high=%.3f retrace<=%.3f",
                            monitorHelper.nvl(losing.getPnlHigh()),
                            monitorHelper.nvl(losing.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
            followUpBySession.remove(session.getId());
        }
    }

    // --- TWO POSITIONS ---
    private void applyTwoPositionsLogic(TradeSession session, BigDecimal price) {
        TradeOrder longOrder = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);
        TradeOrder shortOrder = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT);
        if (longOrder == null || shortOrder == null) return;

        BigDecimal pnlLong = price.subtract(longOrder.getPrice()).divide(longOrder.getPrice(), 8, RoundingMode.HALF_UP).multiply(PERCENTAGE_MULTIPLIER);
        BigDecimal pnlShort = shortOrder.getPrice().subtract(price).divide(shortOrder.getPrice(), 8, RoundingMode.HALF_UP).multiply(PERCENTAGE_MULTIPLIER);

        // best/worst
        boolean longIsBest = pnlLong.compareTo(pnlShort) > 0;
        TradeOrder best = longIsBest ? longOrder : shortOrder;
        TradeOrder worst = longIsBest ? shortOrder : longOrder;
        BigDecimal bestPnl = longIsBest ? pnlLong : pnlShort;
        BigDecimal pnlWorst = longIsBest ? pnlShort : pnlLong; // ⬅ NEW

        // обновляем follow-up ref (максимум из high и текущего best pnl)
        FollowUpState fu = followUpBySession.computeIfAbsent(session.getId(), k -> new FollowUpState());
        fu.setLosingDirection(worst.getDirection());
        BigDecimal bestHigh = monitorHelper.nvl(best.getPnlHigh());
        BigDecimal refCand = bestHigh.max(bestPnl);
        fu.setRefProfit((fu.getRefProfit() == null ? refCand : fu.getRefProfit().max(refCand)));

        // 1) трейлинг у обеих ног: если был активирован — закрываем соответствующую ногу
        if (checkTrailing.checkNewTrailing(longOrder, pnlLong)) {
            log.info("💰 {} [{}] TWO-POS CLOSING LONG: PnL={}%, high={}%, entry={}, current={}",
                    session.getId(),
                    session.getTradePlan(),
                    pnlLong.setScale(3, RoundingMode.HALF_UP),
                    monitorHelper.nvl(longOrder.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
                    longOrder.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));
//            executeClosePosition(session, longOrder, SessionMode.HEDGING,
//                    String.format("two_pos_trailing_long high=%.3f retrace<=%.3f",
//                            monitorHelper.nvl(longOrder.getPnlHigh()),
//                            monitorHelper.nvl(longOrder.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
            routeClose(session, longOrder, SessionMode.HEDGING,
                    String.format("two_pos_trailing_long high=%.3f retrace<=%.3f",
                            monitorHelper.nvl(longOrder.getPnlHigh()),
                            monitorHelper.nvl(longOrder.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
            return;
        }
        if (checkTrailing.checkNewTrailing(shortOrder, pnlShort)) {
            log.info("💰 {} [{}] TWO-POS CLOSING SHORT: PnL={}%, high={}%, entry={}, current={}",
                    session.getId(),
                    session.getTradePlan(),
                    pnlShort.setScale(3, RoundingMode.HALF_UP),
                    monitorHelper.nvl(shortOrder.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
                    shortOrder.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));
//            executeClosePosition(session, shortOrder, SessionMode.HEDGING,
//                    String.format("two_pos_trailing_short high=%.3f retrace<=%.3f",
//                            monitorHelper.nvl(shortOrder.getPnlHigh()),
//                            monitorHelper.nvl(shortOrder.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
            routeClose(session, shortOrder, SessionMode.HEDGING,
                    String.format("two_pos_trailing_short high=%.3f retrace<=%.3f",
                            monitorHelper.nvl(shortOrder.getPnlHigh()),
                            monitorHelper.nvl(shortOrder.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
            return;
        }

        // 2) если best достиг +0.10% — активируем ему трейл (дальше пункт 1 закроет по откату)
        if (bestPnl.compareTo(TWO_POS_PROFITABLE_ACTIVATION_PCT) >= 0 && !Boolean.TRUE.equals(best.getTrailingActive())) {
            best.setTrailingActive(true);
            best.setPnlHigh(bestPnl);
            // baseline для worst зафиксируем уже в single follow-up, когда одна нога останется
            log.info("🎯 {} [{}] TWO-POS BEST {} TRAILING ACTIVATED: {}% (threshold: {}%)",
                    session.getId(), 
                    session.getTradePlan(),
                    best.getDirection(), 
                    bestPnl.setScale(3, RoundingMode.HALF_UP), 
                    TWO_POS_PROFITABLE_ACTIVATION_PCT);
        }

        // 2.5) ПРОВЕРКА УСРЕДНЕНИЯ ПО ХУДШЕЙ НОГЕ ⬅ NEW
        // Условия: есть худшая нога, PnL <= -X%, нет активного усреднения в её направлении, нет кулдауна и т.д.
        if (averaging.checkOpen(session, worst, pnlWorst)) { // твой метод из прошлого сообщения
//            log.info("📊 {} [{}] TWO-POS AVERAGING CHECK {}: PnL={}% - OPENING AVERAGE",
//                    session.getId(),
//                    session.getTradePlan(),
//                    worst.getDirection(),
//                    pnlWorst.setScale(3, RoundingMode.HALF_UP));
            executeOpenAverage(session, worst, String.format("two_pos_averaging dir=%s pnl=%.3f%%", worst.getDirection(), pnlWorst), price);
        }
        // 2.6) ПРОВЕРКА УСРЕДНЕНИЯ ПО ЛУЧШЕЙ НОГЕ ⬅ NEW
        // Условия: есть лучшая нога, PnL <= -X%, нет активного усреднения в её направлении, нет кулдауна и т.д.
//        if (averaging.checkOpen(session, best, bestPnl)) { // твой метод из прошлого сообщения
//            log.info("📊 {} [{}] TWO-POS AVERAGING CHECK {}: PnL={}% - OPENING AVERAGE",
//                    session.getId(),
//                    session.getTradePlan(),
//                    worst.getDirection(),
//                    pnlWorst.setScale(3, RoundingMode.HALF_UP));
//            executeOpenAverage(session, worst, String.format("two_pos_averaging dir=%s pnl=%.3f%%", worst.getDirection(), pnlWorst), price);
//        }

    }

    private void updateSessionInMonitoring(TradeSession updatedSession) {
        if (updatedSession == null) return;
        //new todo
        updatedSession.setProcessing(false);
        if (updatedSession.getStatus() == SessionStatus.COMPLETED) {
            sessions.remove(updatedSession.getId());
            singleTrackBySession.remove(updatedSession.getId());
            followUpBySession.remove(updatedSession.getId());
            log.info("✅ {} completed", updatedSession.getId());
        } else {
            sessions.put(updatedSession.getId(), updatedSession);
        }
    }

    protected boolean isInOrderCooldown(String sessionId) {
        Long last = lastOrderAtMsBySession.get(sessionId);
        return last != null && System.currentTimeMillis() - last < ORDER_COOLDOWN_MS;
    }

    private void markOrderSent(String sessionId) {
        lastOrderAtMsBySession.put(sessionId, System.currentTimeMillis());
    }

    // NEW: единая точка закрытия TODO
    private void routeClose(TradeSession session, TradeOrder candidate, SessionMode mode, String reason) {
        if (candidate == null) return;
        if (OrderPurpose.AVERAGING_OPEN.equals(candidate.getPurpose())) {
            log.info("🔒 {} [{}] ROUTE CLOSE → AVERAGING_CLOSE {}: orderId={}, parent={}",
                    session.getId(), 
                    session.getTradePlan(),
                    candidate.getDirection(),
                    candidate.getOrderId(), 
                    candidate.getParentOrderId());
            executeCloseAverage(session, candidate, mode, reason);
        } else {
            log.info("🔒 {} [{}] ROUTE CLOSE → NORMAL_CLOSE {}: orderId={}, purpose={}",
                    session.getId(), 
                    session.getTradePlan(),
                    candidate.getDirection(),
                    candidate.getOrderId(), 
                    candidate.getPurpose());
            executeClosePosition(session, candidate, mode, reason);
        }
    }

    private void executeClosePosition(TradeSession session, TradeOrder orderToClose, SessionMode mode, String reason) {
        try {
            if (!monitorHelper.isSessionInValidState(session) || !monitorHelper.isValidForClosing(orderToClose)) return;
            if (isInOrderCooldown(session.getId())) return;
            session.setProcessing(true);
            markOrderSent(session.getId());
            TradeSession updated = tradingUpdatesService.closePosition(
                    session, mode, orderToClose.getOrderId(),
                    orderToClose.getRelatedHedgeId(), orderToClose.getDirection(),
                    monitorHelper.determineCloseOrderPurpose(orderToClose),
                    ticker24hService.getPrice(session.getTradePlan()),
                    reason
            );
            updateSessionInMonitoring(updated);
        } catch (Exception e) {
            session.setProcessing(false);
            log.error("closePosition error {}: {}", session.getId(), e.getMessage(), e);
        } finally {
            // если сервис вернул null/упал до update, не держим сессию «залоченной»
            session.setProcessing(false);
        }

    }

    private void executeOpenHedge(TradeSession session, TradingDirection hedgeDir, String purpose, BigDecimal price, String reason) {
        try {
            if (!monitorHelper.isSessionInValidState(session)) return;
            if (session.hasBothPositionsActive()) {
                // log.info("⛔ {} [{}] SKIP OPEN {}: both directions already active", session.getId(), session.getTradePlan(), hedgeDir);
                return;
            }
            if (isInOrderCooldown(session.getId())) {
                log.info("⏱️ {} [{}] SKIP OPEN {}: cooldown", session.getId(), session.getTradePlan(), hedgeDir);
                return;
            }

            // Жёсткий запрет на повтор направления (проверка по флагу и по факту ордеров)
            if (monitorHelper.isDirectionActive(session, hedgeDir)) {
                // log.info("⛔ {} [{}] SKIP OPEN {}: direction already active (flag/orders)", session.getId(), session.getTradePlan(), hedgeDir);
                return;
            }

            session.setProcessing(true);
            markOrderSent(session.getId());

            Long parentOrderId = null;
            // 1) Если MAIN ещё жив — ссылаемся на MAIN
            TradeOrder main = session.getMainOrder();
            if (main != null && main.getOrderId() != null && monitorHelper.isMainStillActive(session)) {
                parentOrderId = main.getOrderId();
            } else {
                // 2) MAIN закрыт — ищем последнего FILLED HEDGE по ПРОТИВОПОЛОЖНОМУ направлению
                TradeOrder link = monitorHelper.getLastFilledHedgeOrderByDirection(session, monitorHelper.opposite(hedgeDir));
                if (link != null) parentOrderId = link.getOrderId();
            }

            // Всегда открываем именно HEDGE (не MAIN)
            TradeSession updated = tradingUpdatesService.openPosition(
                    session, SessionMode.HEDGING, hedgeDir,
                    OrderPurpose.HEDGE_OPEN, price,
                    reason, parentOrderId, null
            );
            updateSessionInMonitoring(updated);
        } catch (Exception e) {
            session.setProcessing(false);
            log.error("openHedge error {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    //open close average
    private void executeOpenAverage(TradeSession session, TradeOrder order, String purpose, BigDecimal price) {
        //demo
        // if (!session.getTradePlan().equals("LINKUSDC")) return;

        try {
            if (!monitorHelper.isSessionInValidState(session)) return;
            if (isInOrderCooldown(session.getId())) {
                //log.info("⏱️ {} [{}] SKIP AVERAGING {}: cooldown", session.getId(), session.getTradePlan(), order.getDirection());
                return;
            }
            // Политика: одно активное усреднение на направление
            if (!monitorHelper.canOpenAverageByDirection(session, order.getDirection())) {
//                //log.info("⛔ {} [{}] SKIP AVERAGING {}: already active for this direction", session.getId(), session.getTradePlan(), order.getDirection());
                return;
            }
            session.setProcessing(true);
            markOrderSent(session.getId());

            Long parentOrderId = order.getOrderId();

            log.info("📊 {} [{}] REQUEST AVERAGE {}: symbol={}, side={}, purpose={}",
                    session.getId(), 
                    session.getTradePlan(),
                    order.getDirection(),
                    order.getSymbol(), 
                    order.getSide(),
                    purpose);
            TradeSession updated = tradingUpdatesService.openAveragePosition(
                    session,
                    SessionMode.HEDGING,
                    order.getDirection(),               // усредняем ту же сторону, что и worst
                    OrderPurpose.AVERAGING_OPEN,            // ключевой purpose
                    price,
                    purpose,
                    parentOrderId
            );
            updateSessionInMonitoring(updated);

        } catch (Exception e) {
            session.setProcessing(false);
            log.error("openAverage error {}: {}", session.getId(), e.getMessage(), e);
        } finally {
            session.setProcessing(false);
        }
    }

    private void executeCloseAverage(TradeSession session, TradeOrder averagingOrder, SessionMode mode, String reason) {
        try {
            if (!monitorHelper.isSessionInValidState(session)) return;
            if (averagingOrder == null) return;
            if (isInOrderCooldown(session.getId())) {
                // log.info("⏱️ {} [{}] SKIP CLOSE AVERAGING {}: cooldown", session.getId(), session.getTradePlan(), averagingOrder.getDirection());
                return;
            }

            session.setProcessing(true);
            markOrderSent(session.getId());

//            BigDecimal price = ticker24hService.getPrice(session.getTradePlan());
//            Long averageOrderId = averagingOrder.getOrderId();
//            Long parentOrderId = averagingOrder.getParentOrderId();

//            TradeSession updated = tradingUpdatesService.closePosition(
//                    session,
//                    averageOrderId,
//                    parentOrderId,
//                    mode,
//                    OrderPurpose.AVERAGING_CLOSE,
//                    price,
//                    reason,
//                    averagingOrder.getDirection()
//            );
            TradeSession updated = tradingUpdatesService.closePosition(
                    session, mode, averagingOrder.getOrderId(),
                    averagingOrder.getRelatedHedgeId(), averagingOrder.getDirection(),
                    OrderPurpose.AVERAGING_CLOSE,
                    ticker24hService.getPrice(session.getTradePlan()),
                    reason
            );
            updateSessionInMonitoring(updated);
        } catch (Exception e) {
            session.setProcessing(false);
            log.error("closeAverage error {}: {}", session.getId(), e.getMessage(), e);
        } finally {
            session.setProcessing(false);
        }
    }

    private BigDecimal calcPnl(TradeOrder active, BigDecimal price) {
        BigDecimal entryPrice = active.getPrice();

        BigDecimal pnl;
        if (active.getDirection() == TradingDirection.LONG) {
            // LONG: ((current_price - entry_price) / entry_price * 100)
            pnl = price.subtract(entryPrice)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP)
                    .multiply(PERCENTAGE_MULTIPLIER);
        } else {
            // SHORT: ((entry_price - current_price) / entry_price * 100)
            pnl = entryPrice.subtract(price)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP)
                    .multiply(PERCENTAGE_MULTIPLIER);
        }
        return pnl;
    }
}
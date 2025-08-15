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
import io.cryptobot.binance.trading.monitoring.v3.utils.ExtraClose;
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
    private final ExtraClose extraClose;
    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;
    private final TradingUpdatesService tradingUpdatesService;
    private final CheckTrailing checkTrailing;

    // === КОНСТАНТЫ === //todo add to config + api update
    private static final long MONITORING_INTERVAL_MS = 1_000;      
    private static final long ORDER_COOLDOWN_MS = 10_000;          

    // PnL в %
    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);

    // Одна позиция: отслеживание и ранний хедж
    private static final BigDecimal SINGLE_TRACKING_START_PCT = new BigDecimal("-0.20"); // Старт отслеживания при -0.20%
    private static final BigDecimal SINGLE_EARLY_HEDGE_PCT = new BigDecimal("-0.20"); // Ранний хедж до трекинга
    private static final BigDecimal SINGLE_WORSEN_DELTA_PCT = new BigDecimal("-0.1"); // Ухудшение от baseline → хедж
    private static final BigDecimal SINGLE_IMPROVE_DELTA_PCT = new BigDecimal("0.1");  // Улучшение > +0.10% → ждём откат ≥30% и хедж


    private final ConcurrentHashMap<String, TradeSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastOrderAtMsBySession = new ConcurrentHashMap<>();
    // Отслеживание одной позиции ДО хеджа: baseline и локальный "soft trailing" для условия 2.2(b)
    private final ConcurrentHashMap<String, SingleTrackState> singleTrackBySession = new ConcurrentHashMap<>();
    // Follow-up у оставшейся ноги ПОСЛЕ закрытия best в двух позициях
    private final ConcurrentHashMap<String, FollowUpState> followUpBySession = new ConcurrentHashMap<>();
    private final ExecutorService monitorPool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    private final TradeSessionLockRegistry lockRegistry;

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
        followUpBySession.remove(idSession);
        lastOrderAtMsBySession.remove(idSession);
    }

    @Scheduled(fixedRate = MONITORING_INTERVAL_MS)
    public void monitor() {
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

                TradeOrder active = monitorHelper.getLatestActiveOrderByDirection(session, dir);
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
        if (checkTrailing.checkTrailing(order, pnl)) {
            log.info("💰 {} [{}] CLOSING {} position: PnL={}%, high={}%, entry={}, current={}",
                    session.getId(),
                    session.getTradePlan(),
                    order.getDirection(),
                    pnl.setScale(3, RoundingMode.HALF_UP),
                    monitorHelper.nvl(order.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
                    order.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));
            routeClose(session, order, SessionMode.SCALPING, trailingReason("single_trailing", order.getPnlHigh()));

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
            executeOpenHedge(session, hedgeDir, price,
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
                executeOpenHedge(session, hedgeDir, price,
                        String.format("single_tracking_worsen delta<=%.3f from %.3f", SINGLE_WORSEN_DELTA_PCT, st.getBaseline()));
                singleTrackBySession.remove(session.getId());
                return;
            }

            // улучшение > +0.10 → включаем «мягкий трейл»: ждём откат ≥80% от high (с учётом комиссии) и ОТКРЫВАЕМ ХЕДЖ
            // FIX: активируем soft-trail только если текущий pnl > 0 (реальная прибыль)
            if (!st.isTrailActive() && checkTrailing.shouldActivateSoftTrailing(delta, pnl, SINGLE_IMPROVE_DELTA_PCT)) {
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
                BigDecimal newTrailHigh = checkTrailing.updateTrailHigh(st.getTrailHigh(), pnl);
                if (newTrailHigh.compareTo(st.getTrailHigh()) > 0) {
                    BigDecimal oldHigh = st.getTrailHigh();
                    st.setTrailHigh(newTrailHigh);
                    log.info("📈 {} [{}] SINGLE SOFT-TRAIL HIGH UPDATED {}: {}% → {}%",
                            session.getId(),
                            session.getTradePlan(),
                            order.getDirection(),
                            oldHigh.setScale(3, RoundingMode.HALF_UP),
                            pnl.setScale(3, RoundingMode.HALF_UP));
                }

                // проверяем soft trailing
                if (checkTrailing.checkSoftTrailing(st.getTrailHigh(), pnl)) {
                    TradingDirection hedgeDir = monitorHelper.opposite(order.getDirection());
                    log.info("🔴 {} [{}] SINGLE SOFT-TRAIL TRIGGERED {}: current={}% <= retrace (high={}%), entry={}, current={}",
                            session.getId(),
                            session.getTradePlan(),
                            hedgeDir,
                            pnl.setScale(3, RoundingMode.HALF_UP),
                            st.getTrailHigh().setScale(3, RoundingMode.HALF_UP),
                            order.getPrice().setScale(8, RoundingMode.HALF_UP),
                            price.setScale(8, RoundingMode.HALF_UP));
                    executeOpenHedge(session, hedgeDir, price,
                            String.format("single_soft_trail_retrace high=%.3f", st.getTrailHigh()));
                    singleTrackBySession.remove(session.getId());
                    return;
                }
            }
        }
    }

    // когда после хеджа остался один ордер
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
            executeOpenHedge(session, monitorHelper.opposite(losing.getDirection()), price,
                    String.format("follow_up_worsen delta<=%.3f from %.3f", SINGLE_WORSEN_DELTA_PCT, fu.getBaseline()));
            followUpBySession.remove(session.getId());
            return;
        }

        // (б) улучшение > +0.10 и затем откат ≥80% от high (учёт комиссии) → ре-хедж
        // FIX: активируем soft-trail только если текущий pnl > 0 (реальная прибыль)
        if (!fu.isSoftTrailActive() && checkTrailing.shouldActivateSoftTrailing(delta, pnl, SINGLE_IMPROVE_DELTA_PCT)) {
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
            BigDecimal newTrailHigh = checkTrailing.updateTrailHigh(fu.getSoftTrailHigh(), pnl);
            if (newTrailHigh.compareTo(fu.getSoftTrailHigh()) > 0) {
                BigDecimal oldHigh = fu.getSoftTrailHigh();
                fu.setSoftTrailHigh(newTrailHigh);
                log.info("📈 {} [{}] FOLLOW-UP SOFT-TRAIL HIGH UPDATED {}: {}% → {}%",
                        session.getId(),
                        session.getTradePlan(),
                        fu.getLosingDirection(),
                        oldHigh.setScale(3, RoundingMode.HALF_UP),
                        pnl.setScale(3, RoundingMode.HALF_UP));
            }

            // проверяем soft trailing
            if (checkTrailing.checkSoftTrailing(fu.getSoftTrailHigh(), pnl)) {
                TradingDirection hedgeDir = monitorHelper.opposite(losing.getDirection());
                log.info("🔴 {} [{}] FOLLOW-UP SOFT-TRAIL TRIGGERED {}: current={}% <= retrace (high={}%), entry={}, current={}",
                        session.getId(),
                        session.getTradePlan(),
                        hedgeDir,
                        pnl.setScale(3, RoundingMode.HALF_UP),
                        fu.getSoftTrailHigh().setScale(3, RoundingMode.HALF_UP),
                        losing.getPrice().setScale(8, RoundingMode.HALF_UP),
                        price.setScale(8, RoundingMode.HALF_UP));
                executeOpenHedge(session, monitorHelper.opposite(losing.getDirection()), price,
                        String.format("follow_up_soft_trail_retrace high=%.3f", fu.getSoftTrailHigh()));
                followUpBySession.remove(session.getId());
                return;
            }
        }

        // (в) или worst сама уйдёт в плюс и закроется по обычному трейлу
        if (checkTrailing.checkTrailing(losing, pnl)) {
            log.info("💰 {} [{}] FOLLOW-UP CLOSING {} position: PnL={}%, high={}%, entry={}, current={}",
                    session.getId(),
                    session.getTradePlan(),
                    losing.getDirection(),
                    pnl.setScale(3, RoundingMode.HALF_UP),
                    monitorHelper.nvl(losing.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
                    losing.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));

            routeClose(session, losing, SessionMode.SCALPING, trailingReason("follow_up_trailing", losing.getPnlHigh()));
            followUpBySession.remove(session.getId());
        }
    }

    // --- TWO POSITIONS ---
    private void applyTwoPositionsLogic(TradeSession session, BigDecimal price) {
        TradeOrder longOrder = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG);
        TradeOrder shortOrder = monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT);
        if (longOrder == null || shortOrder == null) return;

        BigDecimal pnlLong = calcPnl(longOrder, price);
        BigDecimal pnlShort = calcPnl(shortOrder, price);

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
        if (checkTrailing.checkTrailing(best, bestPnl)) {
            log.info("💰 {} [{}] TWO-POS CLOSING {}: PnL={}%, high={}%, entry={}, current={}",
                    session.getId(),
                    session.getTradePlan(),
                    best.getDirection(),
                    bestPnl.setScale(3, RoundingMode.HALF_UP),
                    monitorHelper.nvl(best.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
                    best.getPrice().setScale(8, RoundingMode.HALF_UP),
                    price.setScale(8, RoundingMode.HALF_UP));

            routeClose(session, best, SessionMode.HEDGING, trailingReason("two_pos_trailing", best.getPnlHigh()));
            return;
        }

        // 2) если best достиг +0.10% — активируем ему трейл (дальше пункт 1 закроет по откату)
        checkTrailing.checkTwoPosBestTrailingActivation(bestPnl, best);

        // 2.1) check extra close -> close best order
        if (extraClose.checkExtraClose(session, bestPnl, pnlWorst, best)) {
            routeClose(session, best, SessionMode.HEDGING, String.format("extra_close bestPnl=%.3f worstPnl=%.3f", bestPnl, pnlWorst));
            return;
        }

        // 2.5) ПРОВЕРКА УСРЕДНЕНИЯ ПО ХУДШЕЙ НОГЕ ⬅ NEW
        // Условия: есть худшая нога, PnL <= -X%, нет активного усреднения в её направлении, нет кулдауна и т.д.
        if (averaging.checkOpen(session, worst, pnlWorst)) { // твой метод из прошлого сообщения
            executeOpenAverage(session, worst, String.format("two_pos_averaging dir=%s pnl=%.3f%%", worst.getDirection(), pnlWorst), price);
            return;
        }
        // 2.6) ПРОВЕРКА УСРЕДНЕНИЯ ПО ЛУЧШЕЙ НОГЕ ⬅ NEW
        // Условия: есть лучшая нога, PnL <= -X%, нет активного усреднения в её направлении, нет кулдауна и т.д.
        if (averaging.checkOpen(session, best, bestPnl)) {
            executeOpenAverage(session, best, String.format("two_pos_averaging dir=%s pnl=%.3f%%", best.getDirection(), bestPnl), price);
            return;
        }

    }

    private void updateSessionInMonitoring(TradeSession updatedSession) {
        if (updatedSession == null) return;
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

    // close all type orders here
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

    private void executeOpenHedge(TradeSession session, TradingDirection hedgeDir, BigDecimal price, String reason) {
        try {
            if (!monitorHelper.isSessionInValidState(session)) return;
            if (session.hasBothPositionsActive()) {
                return;
            }
            if (isInOrderCooldown(session.getId())) {
                log.info("⏱️ {} [{}] SKIP OPEN {}: cooldown", session.getId(), session.getTradePlan(), hedgeDir);
                return;
            }

            // Жёсткий запрет на повтор направления (проверка по флагу и по факту ордеров)
            if (monitorHelper.isDirectionActive(session, hedgeDir)) {
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
            log.error("openHedge error {}: {}", session.getId(), e.getMessage(), e);
        }finally {
            session.setProcessing(false);
        }
    }

    //open close average
    private void executeOpenAverage(TradeSession session, TradeOrder order, String purpose, BigDecimal price) {
        try {
            if (!monitorHelper.isSessionInValidState(session)) return;
            if (isInOrderCooldown(session.getId())) {
                return;
            }
            // one average for one direction
            if (!monitorHelper.canOpenAverageByDirection(session, order.getDirection())) {
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
                return;
            }

            session.setProcessing(true);
            markOrderSent(session.getId());

            TradeSession updated = tradingUpdatesService.closePosition(
                    session, mode, averagingOrder.getOrderId(),
                    averagingOrder.getRelatedHedgeId(), averagingOrder.getDirection(),
                    OrderPurpose.AVERAGING_CLOSE,
                    ticker24hService.getPrice(session.getTradePlan()),
                    reason
            );
            updateSessionInMonitoring(updated);
        } catch (Exception e) {
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

    private String trailingReason(String tag, BigDecimal high) {
        BigDecimal h = monitorHelper.nvl(high);
        return String.format("%s high=%.3f retrace<=%.3f", tag, h, checkTrailing.computeRetraceLevel(h));
    }
}
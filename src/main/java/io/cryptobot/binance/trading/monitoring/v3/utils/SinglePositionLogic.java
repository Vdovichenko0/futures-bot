//package io.cryptobot.binance.trading.monitoring.v3.utils;
//
//import io.cryptobot.binance.trade.session.enums.SessionMode;
//import io.cryptobot.binance.trade.session.enums.TradingDirection;
//import io.cryptobot.binance.trade.session.model.TradeOrder;
//import io.cryptobot.binance.trade.session.model.TradeSession;
//import io.cryptobot.binance.trade.session.service.TradeSessionService;
//import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
//import io.cryptobot.binance.trading.monitoring.v3.models.FollowUpState;
//import io.cryptobot.binance.trading.monitoring.v3.models.SingleTrackState;
//import io.cryptobot.binance.trading.updates.TradingUpdatesService;
//import io.cryptobot.market_data.ticker24h.Ticker24hService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class SinglePositionLogic {
//    // PnL в %
//    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);
//
//    // Комиссия (в проц.пунктах), учитываем при трейлинге
//    private static final BigDecimal COMMISSION_PCT = new BigDecimal("0.036"); // 0.036%
//
//    // Трейлинг
//    private static final BigDecimal TRAILING_ACTIVATION_THRESHOLD_PCT = new BigDecimal("0.10"); // Активация при +0.10%
//    private static final BigDecimal TRAILING_CLOSE_RETRACE_RATIO     = new BigDecimal("0.80");  // Закрытие при 20% отката
//
//    // Одна позиция: отслеживание и ранний хедж
//    private static final BigDecimal SINGLE_TRACKING_START_PCT        = new BigDecimal("-0.20"); // Старт отслеживания при -0.20%
//    private static final BigDecimal SINGLE_EARLY_HEDGE_PCT           = new BigDecimal("-0.10"); // Ранний хедж до трекинга
//    private static final BigDecimal SINGLE_WORSEN_DELTA_PCT          = new BigDecimal("-0.10"); // Ухудшение от baseline → хедж
//    private static final BigDecimal SINGLE_IMPROVE_DELTA_PCT         = new BigDecimal("0.10");  // Улучшение > +0.10% → ждём откат ≥30% и хедж
//
//    // Две позиции
//    private static final BigDecimal TWO_POS_PROFITABLE_ACTIVATION_PCT= new BigDecimal("0.10");  // Порог активации трейла у best
//
//    // === СЕРВИСЫ И СОСТОЯНИЕ ===
//    private final TradeSessionService sessionService;
//    private final Ticker24hService ticker24hService;
//    private final TradingUpdatesService tradingUpdatesService;
//    private final CheckTrailing checkTrailing;
//    private final MonitorHelper monitorHelper;
//
//    // --- SINGLE POSITION ---
//    private void applySinglePositionLogic(TradeSession session, BigDecimal price, TradeOrder order, BigDecimal pnl) {
//        // если уже есть follow-up (после двух ног) — работаем строго с убыточной ногой
//        FollowUpState fu = followUpBySession.get(session.getId());
//        if (fu != null) {
//            handleFollowUpSingle(session, price, fu);
//            return;
//        }
//
//        // обычный трейлинг в плюс → закрываем
//        if (checkTrailing.checkNewTrailing(order, pnl)) {
//            log.info("💰 {} CLOSING position: PnL={}%, high={}%, entry={}, current={}, direction={}",
//                    session.getId(),
//                    pnl.setScale(3, RoundingMode.HALF_UP),
//                    monitorHelper.nvl(order.getPnlHigh()).setScale(3, RoundingMode.HALF_UP),
//                    order.getPrice().setScale(8, RoundingMode.HALF_UP),
//                    price.setScale(8, RoundingMode.HALF_UP),
//                    order.getDirection());
//            executeClosePosition(session, order, SessionMode.SCALPING,
//                    String.format("single_trailing high=%.3f retrace<=%.3f",
//                            monitorHelper.nvl(order.getPnlHigh()),
//                            monitorHelper.nvl(order.getPnlHigh()).multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT)));
//            singleTrackBySession.remove(session.getId());
//            return;
//        }
//
//        // ранний хедж до начала трекинга
//        SingleTrackState st = singleTrackBySession.get(session.getId());
//        if (st == null && pnl.compareTo(SINGLE_EARLY_HEDGE_PCT) <= 0) {
//            TradingDirection hedgeDir = monitorHelper.opposite(order.getDirection());
//            log.info("⚠️ {} early hedge: pnl={}% <= {}% (before tracking), entry={}, current={}, direction={}",
//                    session.getId(), pnl.setScale(3, RoundingMode.HALF_UP), SINGLE_EARLY_HEDGE_PCT,
//                    order.getPrice().setScale(8, RoundingMode.HALF_UP),
//                    price.setScale(8, RoundingMode.HALF_UP),
//                    order.getDirection());
//            executeOpenHedge(session, hedgeDir, "HEDGE_OPEN", price,
//                    String.format("early_hedge pnl<=%.3f before_tracking", SINGLE_EARLY_HEDGE_PCT));
//            return;
//        }
//
//        // старт трекинга при -0.20%
//        if (st == null && pnl.compareTo(SINGLE_TRACKING_START_PCT) <= 0) {
//            st = SingleTrackState.builder()
//                    .baseline(pnl)
//                    .tracking(true)
//                    .trailActive(false)
//                    .trailHigh(pnl) // от этого уровня начнём улучшение
//                    .build();
//            singleTrackBySession.put(session.getId(), st);
//            log.info("🧭 {} single tracking start baseline={}%", session.getId(), st.getBaseline());
//            return;
//        }
//
//        // если трекинг активен — работаем по дельтам к baseline
//        if (st != null && st.isTracking()) {
//            BigDecimal delta = pnl.subtract(st.getBaseline());
//
//            // ухудшение -0.10 → хедж
//            if (delta.compareTo(SINGLE_WORSEN_DELTA_PCT) <= 0) {
//                TradingDirection hedgeDir = monitorHelper.opposite(order.getDirection());
//                log.info("📉 {} single tracking WORSEN: delta={}% <= {}% (baseline={}%), current PnL={}%, entry={}, current={}",
//                        session.getId(), delta.setScale(3, RoundingMode.HALF_UP),
//                        SINGLE_WORSEN_DELTA_PCT, st.getBaseline().setScale(3, RoundingMode.HALF_UP),
//                        pnl.setScale(3, RoundingMode.HALF_UP),
//                        order.getPrice().setScale(8, RoundingMode.HALF_UP),
//                        price.setScale(8, RoundingMode.HALF_UP));
//                executeOpenHedge(session, hedgeDir, "HEDGE_OPEN", price,
//                        String.format("single_tracking_worsen delta<=%.3f from %.3f", SINGLE_WORSEN_DELTA_PCT, st.getBaseline()));
//                singleTrackBySession.remove(session.getId());
//                return;
//            }
//
//            // улучшение > +0.10 → включаем «мягкий трейл»: ждём откат ≥80% от high (с учётом комиссии) и ОТКРЫВАЕМ ХЕДЖ
//            // FIX: активируем soft-trail только если текущий pnl > 0 (реальная прибыль)
//            if (!st.isTrailActive() && delta.compareTo(SINGLE_IMPROVE_DELTA_PCT) > 0 && pnl.compareTo(BigDecimal.ZERO) > 0) {
//                st.setTrailActive(true);
//                st.setTrailHigh(pnl);
//                log.info("🚀 {} single soft-trail ENABLED: delta={}% > {}% (baseline={}%), pnl>0",
//                        session.getId(), delta.setScale(3, RoundingMode.HALF_UP),
//                        SINGLE_IMPROVE_DELTA_PCT, st.getBaseline().setScale(3, RoundingMode.HALF_UP));
//            }
//            if (st.isTrailActive()) {
//                // обновляем локальный high
//                if (pnl.compareTo(st.getTrailHigh()) > 0) {
//                    BigDecimal oldHigh = st.getTrailHigh();
//                    st.setTrailHigh(pnl);
//                    log.info("📈 {} single soft-trail high updated: {}% → {}%",
//                            session.getId(), oldHigh.setScale(3, RoundingMode.HALF_UP), pnl.setScale(3, RoundingMode.HALF_UP));
//                }
//
//                // FIX: если high ≤ 0 — не армим откат (ещё нет прибыли для трейла)
//                if (st.getTrailHigh().compareTo(BigDecimal.ZERO) <= 0) {
//                    return; // ждём, пока выйдем в положительную зону
//                }
//
//                BigDecimal softRetrace = st.getTrailHigh().multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT);
//                // FIX: не обнуляем softRetrace до 0; если ≤0 — не триггерим
//                if (softRetrace.compareTo(BigDecimal.ZERO) > 0 && pnl.compareTo(softRetrace) <= 0) {
//                    TradingDirection hedgeDir = monitorHelper.opposite(order.getDirection());
//                    log.info("🔴 {} single soft-trail TRIGGERED: current={}% <= retrace={}% (high={}%), entry={}, current={}",
//                            session.getId(), pnl.setScale(3, RoundingMode.HALF_UP),
//                            softRetrace.setScale(3, RoundingMode.HALF_UP),
//                            st.getTrailHigh().setScale(3, RoundingMode.HALF_UP),
//                            order.getPrice().setScale(8, RoundingMode.HALF_UP),
//                            price.setScale(8, RoundingMode.HALF_UP));
//                    executeOpenHedge(session, hedgeDir, "HEDGE_OPEN", price,
//                            String.format("single_soft_trail_retrace high=%.3f retrace<=%.3f", st.getTrailHigh(), softRetrace));
//                    singleTrackBySession.remove(session.getId());
//                    return;
//                }
//            }
//        }
//    }
//}

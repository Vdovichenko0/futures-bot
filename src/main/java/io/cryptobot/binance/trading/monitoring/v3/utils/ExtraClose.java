package io.cryptobot.binance.trading.monitoring.v3.utils;

import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trading.monitoring.v3.models.ExtraCloseState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtraClose {
    private final Map<String, ExtraCloseState> tracking = new ConcurrentHashMap<>();
    private static final BigDecimal BEST_ORDER = BigDecimal.valueOf(-0.20);
    private static final BigDecimal LOW_ORDER = BigDecimal.valueOf(-0.50);
    private static final BigDecimal POSITION_GO_DOWN = BigDecimal.valueOf(-0.1);
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(5);

    public boolean checkExtraClose(TradeSession session, BigDecimal pnlBest, BigDecimal pnlWorst, TradeOrder bestOrder) {
        ExtraCloseState state = tracking.get(session.getId());

        if (state != null) {
            // 1. check duration
            if (Duration.between(state.startTime(), LocalDateTime.now()).compareTo(MAX_LIFETIME) > 0) {
//                log.info("⏳ {} [{}] EXTRA CLOSE expired after {} min (orderId={})", session.getId(), session.getTradePlan(), MAX_LIFETIME.toMinutes(), state.orderId());
                tracking.remove(session.getId());
                return false;
            }

            // 2. if position go down
            if (hasGoneDown(state, pnlBest)) {
//                log.info("📉 {} [{}] EXTRA CLOSE TRIGGERED for orderId={} baseline={}%", session.getId(), session.getTradePlan(), state.orderId(), state.baseline());
                tracking.remove(session.getId());
                return true;
            }

            // 3. monitoring
            return false;
        }

        // 4. create monitoring
        if (shouldStartExtraClose(pnlBest, pnlWorst)) {
            start(session, bestOrder, pnlBest);
        }

        return false;
    }

    private boolean shouldStartExtraClose(BigDecimal pnlBest, BigDecimal pnlWorst) {
        if (pnlBest.compareTo(BigDecimal.ZERO) < 0 && pnlWorst.compareTo(BigDecimal.ZERO) < 0) {
            return pnlBest.compareTo(BEST_ORDER) <= 0 && pnlWorst.compareTo(LOW_ORDER) <= 0;
        }
        return false;
    }

    private void start(TradeSession session, TradeOrder order, BigDecimal pnlBest) {
        tracking.put(session.getId(), new ExtraCloseState(order.getOrderId(), pnlBest, LocalDateTime.now()));
//        log.info("⚡ {} [{}] EXTRA CLOSE MONITORING STARTED for orderId={} baseline={}%", session.getId(), session.getTradePlan(), order.getOrderId(), pnlBest);
    }

    private boolean hasGoneDown(ExtraCloseState state, BigDecimal currentPnl) {
        return currentPnl.subtract(state.baseline()).compareTo(POSITION_GO_DOWN) <= 0;
    }

}
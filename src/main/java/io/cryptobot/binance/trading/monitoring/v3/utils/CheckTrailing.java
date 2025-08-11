package io.cryptobot.binance.trading.monitoring.v3.utils;

import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckTrailing {
    private final MonitorHelper monitorHelper;
    private static final BigDecimal COMMISSION_PCT = new BigDecimal("0.036"); // 0.036%
    private static final BigDecimal TRAILING_ACTIVATION_THRESHOLD_PCT = new BigDecimal("0.10");
    private static final BigDecimal TRAILING_CLOSE_RETRACE_RATIO     = new BigDecimal("0.80");  // Закрытие при 20% отката

    public boolean checkNewTrailing(TradeOrder order, BigDecimal currentPnl) {
        if (currentPnl.compareTo(monitorHelper.nvl(order.getPnlHigh())) > 0) {
            BigDecimal oldHigh = order.getPnlHigh();
            order.setPnlHigh(currentPnl);
            if (oldHigh != null && currentPnl.compareTo(oldHigh) > 0) {
                log.info("📈 {} trailing high updated: {}% → {}%",
                        order.getOrderId(), oldHigh.setScale(3, RoundingMode.HALF_UP), currentPnl.setScale(3, RoundingMode.HALF_UP));
            }
        }

        boolean isActive = Boolean.TRUE.equals(order.getTrailingActive());
        if (!isActive && currentPnl.compareTo(TRAILING_ACTIVATION_THRESHOLD_PCT) >= 0) {
            order.setTrailingActive(true);
            order.setPnlHigh(currentPnl);
            log.info("🎯 {} trailing ACTIVATED at {}% (threshold: {}%)",
                    order.getOrderId(), currentPnl.setScale(3, RoundingMode.HALF_UP), TRAILING_ACTIVATION_THRESHOLD_PCT);
            return false;
        }

        if (isActive && order.getPnlHigh()!=null) {
            BigDecimal retraceLevel = order.getPnlHigh().multiply(TRAILING_CLOSE_RETRACE_RATIO).subtract(COMMISSION_PCT);
            if (retraceLevel.compareTo(BigDecimal.ZERO) < 0) retraceLevel = BigDecimal.ZERO;
            if (currentPnl.compareTo(retraceLevel) <= 0) {
                order.setTrailingActive(false);
                log.info("🔴 {} trailing TRIGGERED: current={}% <= retrace={}% (high={}%)",
                        order.getOrderId(), currentPnl.setScale(3, RoundingMode.HALF_UP),
                        retraceLevel.setScale(3, RoundingMode.HALF_UP),
                        order.getPnlHigh().setScale(3, RoundingMode.HALF_UP));
                return true; // закрывать позицию
            }
        }
        return false;
    }
}

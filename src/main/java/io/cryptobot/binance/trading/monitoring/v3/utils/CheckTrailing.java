package io.cryptobot.binance.trading.monitoring.v3.utils;

import io.cryptobot.binance.order.enums.OrderSide;
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

	// === КОНСТАНТЫ (в проц.пунктах PnL) ===
	private static final BigDecimal COMMISSION_PCT = new BigDecimal("0.036");         // комиссия 0.036%
	private static final BigDecimal TRAIL_ACTIVATION_PCT = new BigDecimal("0.20");    // активация трейла при >= 0.20%
	private static final BigDecimal TWO_POS_TRAIL_ACTIVATION_PCT = TRAIL_ACTIVATION_PCT;

	//  - high ≤ 0.30% → откат 30%
	//  - 0.30% < high ≤ 0.50% → откат 20%
	//  - high  > 0.50% → откат 10%
	private static final BigDecimal THRESHOLD_0_30 = new BigDecimal("0.30");
	private static final BigDecimal THRESHOLD_0_50 = new BigDecimal("0.50");
	private static final BigDecimal RETRACE_30     = new BigDecimal("0.30");
	private static final BigDecimal RETRACE_20     = new BigDecimal("0.20");
	private static final BigDecimal RETRACE_10     = new BigDecimal("0.10");

	// Soft-trailing: «откат 20%»
	private static final BigDecimal SOFT_RETRACE_PCT = RETRACE_20;

	// ================= ПУБЛИЧНЫЙ API =================

	/**
	 * Универсальная проверка трейлинга для любой позиции.
	 * @return true — если нужно закрывать позицию (сработал откат).
	 */
	public boolean checkTrailing(TradeOrder order, BigDecimal currentPnl) {
		liftHighIfNeeded(order, currentPnl);

		final boolean isActive = Boolean.TRUE.equals(order.getTrailingActive());

		// Активация
		if (!isActive && shouldActivateTrailing(currentPnl)) {
			activateTrailing(order, currentPnl, "STANDARD");
			return false;
		}

		// Триггер
		if (isActive && order.getPnlHigh() != null) {
			return triggerIfRetraced(order, currentPnl);
		}
		return false;
	}

	/**
	 * Soft-trailing: проверка отката от локального high (фиксированный откат SOFT_RETRACE_PCT).
	 * @return true — если условие срабатывания выполнено.
	 */
	public boolean checkSoftTrailing(BigDecimal trailHigh, BigDecimal currentPnl) {
		if (isNotPositive(trailHigh)) return false;
		BigDecimal level = levelForRetrace(trailHigh, SOFT_RETRACE_PCT);
		return level.signum() > 0 && currentPnl.compareTo(level) <= 0;
	}

	/**
	 * Две позиции: активация трейла у best при достижении порога прибыли.
	 * @return true — если трейл был активирован сейчас.
	 */
	public boolean checkTwoPosBestTrailingActivation(BigDecimal bestPnl, TradeOrder order) {
		if (bestPnl.compareTo(TWO_POS_TRAIL_ACTIVATION_PCT) >= 0 && !Boolean.TRUE.equals(order.getTrailingActive())) {
			activateTrailing(order, bestPnl, "TWO-POS BEST");
			return true;
		}
		return false;
	}

	/**
	 * Условие включения soft-trailing при single/follow-up трекинге:
	 * активируем, только если улучшение от baseline > порога и текущий pnl > 0.
	 */
	public boolean shouldActivateSoftTrailing(BigDecimal deltaFromBaseline, BigDecimal currentPnl, BigDecimal improveDeltaPct) {
		return deltaFromBaseline.compareTo(improveDeltaPct) > 0 && currentPnl.compareTo(BigDecimal.ZERO) > 0;
	}

	/**
	 * Обновляет локальный high для soft-trailing: возвращает новое значение.
	 */
	public BigDecimal updateTrailHigh(BigDecimal currentTrailHigh, BigDecimal currentPnl) {
		return (currentTrailHigh == null || currentPnl.compareTo(currentTrailHigh) > 0) ? currentPnl : currentTrailHigh;
	}

	/**
	 * Публичный помощник: уровень PnL (с комиссией), при котором сработает трейл
	 * для данного pnlHigh по адаптивным правилам ТЗ.
	 */
	public BigDecimal computeRetraceLevel(BigDecimal pnlHigh) {
		return levelForRetrace(pnlHigh, adaptiveRetracePct(pnlHigh));
	}

	// ================= ВНУТРЕННЯЯ ЛОГИКА =================

	private void liftHighIfNeeded(TradeOrder order, BigDecimal currentPnl) {
		BigDecimal high = monitorHelper.nvl(order.getPnlHigh());
		if (currentPnl.compareTo(high) > 0) {
			BigDecimal old = order.getPnlHigh();
			order.setPnlHigh(currentPnl);
			if (old != null) logTrailHighUpdate(order, old, currentPnl);
		}
	}

	private boolean shouldActivateTrailing(BigDecimal currentPnl) {
		return currentPnl.compareTo(TRAIL_ACTIVATION_PCT) >= 0;
	}

	private void activateTrailing(TradeOrder order, BigDecimal atPnl, String context) {
		order.setTrailingActive(true);
		order.setPnlHigh(atPnl);
		logTrailingActivation(order, atPnl, context);
	}

	private boolean triggerIfRetraced(TradeOrder order, BigDecimal currentPnl) {
		BigDecimal high = order.getPnlHigh();
		BigDecimal retracePct = adaptiveRetracePct(high);
		BigDecimal level = levelForRetrace(high, retracePct);
		if (currentPnl.compareTo(level) <= 0) {
			order.setTrailingActive(false);
			logTrailingTrigger(order, currentPnl, level, retracePct);
			return true;
		}
		return false;
	}

	/**
	 * Адаптивный откат по ТЗ по значению high.
	 * Возвращает долю отката (0.30 / 0.20 / 0.10).
	 */
	private BigDecimal adaptiveRetracePct(BigDecimal pnlHigh) {
		if (pnlHigh == null) return RETRACE_30; // по умолчанию — консервативно
		if (pnlHigh.compareTo(THRESHOLD_0_30) <= 0) return RETRACE_30;
		if (pnlHigh.compareTo(THRESHOLD_0_50) <= 0) return RETRACE_20;
		return RETRACE_10;
	}

	/**
	 * Унифицированный расчёт уровня срабатывания по «доле отката».
	 * trigger = high * (1 - откат) - комиссия, но не ниже 0.
	 */
	private BigDecimal levelForRetrace(BigDecimal high, BigDecimal retracePct) {
		if (isNotPositive(high)) return BigDecimal.ZERO;
		BigDecimal keep = BigDecimal.ONE.subtract(retracePct); // сколько «держим» от high
		BigDecimal lvl = high.multiply(keep).subtract(COMMISSION_PCT);
		return lvl.max(BigDecimal.ZERO);
	}

	private boolean isNotPositive(BigDecimal v) {
		return v == null || v.signum() <= 0;
	}

	// ================= ЛОГИ =================
	private void logTrailHighUpdate(TradeOrder order, BigDecimal oldHigh, BigDecimal newHigh) {
		log.info("📈 {} {} trailing high updated: {}% → {}%", order.getSymbol(), side(order), s3(oldHigh), s3(newHigh));
	}

	private void logTrailingActivation(TradeOrder order, BigDecimal pnl, String context) {
		log.info("🎯 {} {} {} trailing ACTIVATED: {}% (threshold: {}%)", order.getSymbol(), side(order), context, s3(pnl), s3(TRAIL_ACTIVATION_PCT));
	}

	private void logTrailingTrigger(TradeOrder order, BigDecimal currentPnl, BigDecimal retraceLevel, BigDecimal retracePct) {
		log.info("🔴 {} {} trailing TRIGGERED: current={}% <= retrace={}% (high={}%, retrace={}%)",
				order.getSymbol(), side(order),
				s3(currentPnl), s3(retraceLevel), s3(order.getPnlHigh()),
				s1(retracePct.multiply(BigDecimal.valueOf(100))));
	}

	private String side(TradeOrder order) {
		return order.getSide() == OrderSide.BUY ? "LONG" : "SHORT";
	}

	// formatting helpers for logs
	private static BigDecimal s3(BigDecimal v) { return v == null ? BigDecimal.ZERO : v.setScale(3, RoundingMode.HALF_UP); }
	private static BigDecimal s1(BigDecimal v) { return v == null ? BigDecimal.ZERO : v.setScale(1, RoundingMode.HALF_UP); }
}

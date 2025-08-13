package io.cryptobot.binance.trading.monitoring.v3.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param orderId   Ордер, который мониторим
 * @param baseline  PnL на момент старта
 * @param startTime timestamp старта мониторинга
 */
public record ExtraCloseState(Long orderId, BigDecimal baseline, LocalDateTime startTime) {
}


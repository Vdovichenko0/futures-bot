package io.cryptobot.binance.trading.monitoring.v3.models;

import io.cryptobot.binance.trade.session.enums.TradingDirection;
import lombok.*;

import java.math.BigDecimal;

/**
 * Состояние follow-up оставшейся ноги после закрытия best.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FollowUpState {
    private TradingDirection losingDirection; // направление оставшейся ноги
    private BigDecimal refProfit;             // опорная прибыль пары (макс(bestHigh, bestPnl))
    private BigDecimal baseline;              // baseline для убыточной ноги при старте follow-up
    private boolean baselineFixed;            // зафиксирован ли baseline
    private boolean softTrailActive;          // включён ли мягкий трейл (improve+retrace)
    private BigDecimal softTrailHigh;         // хай для мягкого трейла
}



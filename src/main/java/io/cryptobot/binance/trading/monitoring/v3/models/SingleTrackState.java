package io.cryptobot.binance.trading.monitoring.v3.models;

import lombok.*;

import java.math.BigDecimal;

/**
 * Трекинг одной позиции до открытия хеджа.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SingleTrackState {
    private BigDecimal baseline;        // PnL на старте трекинга
    private boolean tracking;         // включён ли трекинг
    private boolean trailActive;      // включён ли «мягкий трейл» (для условия improve+retrace)
    private BigDecimal trailHigh;       // хайлвл для мягкого трейла
}



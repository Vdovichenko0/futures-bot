package io.cryptobot.binance.trade.trade_plan.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TradeMetricsDto {
    private Double minLongPct;
    private Double minShortPct;
    private Double minImbalanceLong;
    private Double maxImbalanceShort;
    private Double emaSensitivity;
    private Double volRatioThreshold;
    private int volWindowSec;
    private int depthLevels;
}

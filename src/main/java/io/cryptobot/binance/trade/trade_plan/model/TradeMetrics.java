package io.cryptobot.binance.trade.trade_plan.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TradeMetrics {
//    private BigDecimal volume24h;
//    private BigDecimal volume1m;
    private BigDecimal imbalance;
    private BigDecimal longShortRatio;
}

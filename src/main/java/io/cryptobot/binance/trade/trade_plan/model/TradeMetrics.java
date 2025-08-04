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
    //todo add ema metrics (need more info from Dima)
//    private BigDecimal ema20;
//    private BigDecimal ema50;
    private BigDecimal imbalance;
    private BigDecimal longShortRatio;

    public void updateImbalance(BigDecimal imb){
        imbalance = imb;
    }

    public void updateRatio(BigDecimal ratio){
        longShortRatio = ratio;
    }
}

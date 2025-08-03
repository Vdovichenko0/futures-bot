package io.cryptobot.binance.trade.trade_plan.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SizeModel {
    private BigDecimal tickSize;
    private BigDecimal lotSize;
    private BigDecimal minCount;
    private BigDecimal minAmount;
}

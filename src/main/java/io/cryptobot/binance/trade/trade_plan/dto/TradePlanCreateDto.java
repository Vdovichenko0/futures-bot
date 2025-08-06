package io.cryptobot.binance.trade.trade_plan.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TradePlanCreateDto {
    private String symbol;
    private TradeMetricsDto metrics;
    private BigDecimal amountPerTrade;
    private int leverage;
}
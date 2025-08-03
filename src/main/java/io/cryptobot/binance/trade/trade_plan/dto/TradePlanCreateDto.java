package io.cryptobot.binance.trade.trade_plan.dto;

import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
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
    private TradeMetrics metrics;
    private BigDecimal amountPerTrade;
    private int leverage;
}
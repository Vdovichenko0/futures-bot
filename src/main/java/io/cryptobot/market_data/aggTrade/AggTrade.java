package io.cryptobot.market_data.aggTrade;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AggTrade {
    private String symbol;             // s
    private long eventTime;            // E
    private long aggregateTradeId;     // a
    private BigDecimal price;          // p
    private BigDecimal quantity;       // q
    private long firstTradeId;         // f
    private long lastTradeId;          // l
    private long tradeTime;            // T
    private boolean buyerIsMaker;      // m
}

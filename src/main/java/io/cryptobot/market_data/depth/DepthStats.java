package io.cryptobot.market_data.depth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepthStats {
    private String symbol;
    private int bidsCount;
    private int asksCount;
    private long lastUpdateId;
    private long memoryUsageBytes;
    
    public DepthStats(String symbol, int bidsCount, int asksCount, long lastUpdateId) {
        this.symbol = symbol;
        this.bidsCount = bidsCount;
        this.asksCount = asksCount;
        this.lastUpdateId = lastUpdateId;
        this.memoryUsageBytes = 0; // Можно добавить расчет памяти позже
    }
} 
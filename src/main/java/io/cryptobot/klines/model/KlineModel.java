package io.cryptobot.klines.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.cryptobot.klines.enums.IntervalE;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KlineModel {
    private long openTime;

    private long closeTime;

    private String symbol;

    private IntervalE interval;

    private BigDecimal openPrice;

    private BigDecimal closePrice;

    private BigDecimal highPrice;

    private BigDecimal lowPrice;

    private BigDecimal volume;

    private BigDecimal quoteAssetVolume;

    private long numberOfTrades;

    private BigDecimal takerBuyBaseVolume;

    private BigDecimal takerBuyQuoteVolume;

    private boolean isClosed;

    public Instant getOpenTimeInstant() {
        return Instant.ofEpochMilli(openTime);
    }

    public Instant getCloseTimeInstant() {
        return Instant.ofEpochMilli(closeTime);
    }
}

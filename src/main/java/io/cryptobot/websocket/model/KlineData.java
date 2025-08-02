package io.cryptobot.websocket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class KlineData {

    @JsonProperty("t")
    private long openTime; // Время открытия свечи (UTC в миллисекундах)

    @JsonProperty("T")
    private long closeTime; // Время закрытия свечи (UTC в миллисекундах)

    @JsonProperty("s")
    private String symbol; // Символ (например, "BTCUSDT")

    @JsonProperty("i")
    private String interval; // Интервал свечи (например, "1m", "5m")

    @JsonProperty("f")
    private long firstTradeId; // ID первой сделки в свече

    @JsonProperty("L")
    private long lastTradeId; // ID последней сделки в свече

    @JsonProperty("o")
    private BigDecimal openPrice; // Цена открытия

    @JsonProperty("c")
    private BigDecimal closePrice; // Цена закрытия

    @JsonProperty("h")
    private BigDecimal highPrice; // Максимальная цена

    @JsonProperty("l")
    private BigDecimal lowPrice; // Минимальная цена

    @JsonProperty("v")
    private BigDecimal volume; // Объем торгов (базовый актив)

    @JsonProperty("n")
    private long numberOfTrades; // Количество сделок

    @JsonProperty("x")
    private boolean isClosed; // Закрыта ли свеча?

    @JsonProperty("q")
    private BigDecimal quoteVolume; // Объем торгов в котируемой валюте

    @JsonProperty("V")
    private BigDecimal takerBuyVolume; // Объем торгов покупателей

    @JsonProperty("Q")
    private BigDecimal takerBuyQuoteVolume; // Объем торгов покупателей в котируемой валюте

    @JsonProperty("B")
    private String ignore; // Игнорируемое поле
}
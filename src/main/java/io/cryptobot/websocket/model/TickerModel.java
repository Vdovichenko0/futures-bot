package io.cryptobot.websocket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TickerModel {

    @JsonProperty("e")
    private String eventType; // Тип события ("24hrTicker")

    @JsonProperty("E")
    private long eventTime; // Время события (в миллисекундах)

    @JsonProperty("s")
    private String symbol; // Торговая пара (BTCUSDT, ETHUSDT)

    @JsonProperty("p")
    private BigDecimal priceChange; // Изменение цены за 24ч

    @JsonProperty("P")
    private BigDecimal priceChangePct; // Изменение цены в %

    @JsonProperty("w")
    private BigDecimal weightedAvg; // Средневзвешенная цена

    @JsonProperty("x")
    private BigDecimal firstTradePrice; // Первая цена сделки за 24ч

    @JsonProperty("c")
    private BigDecimal lastPrice; // Последняя цена

    @JsonProperty("Q")
    private BigDecimal lastQty; // Объем последней сделки

    @JsonProperty("b")
    private BigDecimal bidPrice; // Лучшая цена покупки

    @JsonProperty("B")
    private BigDecimal bidQty; // Объем лучшей покупки

    @JsonProperty("a")
    private BigDecimal askPrice; // Лучшая цена продажи

    @JsonProperty("A")
    private BigDecimal askQty; // Объем лучшей продажи

    @JsonProperty("o")
    private BigDecimal openPrice; // Цена открытия за 24ч

    @JsonProperty("h")
    private BigDecimal highPrice; // Максимальная цена за 24ч

    @JsonProperty("l")
    private BigDecimal lowPrice; // Минимальная цена за 24ч

    @JsonProperty("v")
    private BigDecimal volume; // Объем торгов за 24ч (в базовом активе)

    @JsonProperty("q")
    private BigDecimal quoteVolume; // Объем торгов в котируемой валюте за 24ч

    @JsonProperty("O")
    private long statisticsOpenTime; // Время начала 24-часового окна

    @JsonProperty("C")
    private long statisticsCloseTime; // Время окончания 24-часового окна

    @JsonProperty("F")
    private long firstTradeId; // ID первой сделки

    @JsonProperty("L")
    private long lastTradeId; // ID последней сделки

    @JsonProperty("n")
    private long totalTrades; // Общее количество сделок за 24ч
}

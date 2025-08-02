package io.cryptobot.websocket.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeModel {

    @JsonProperty("e")
    private String eventType; // Тип события ("trade")

    @JsonProperty("E")
    private long eventTime; // Время события (UTC в миллисекундах)

    @JsonProperty("s")
    private String symbol; // Символ (например, "BTCUSDT")

    @JsonProperty("t")
    private long tradeId; // ID сделки

    @JsonProperty("p")
    private BigDecimal price; // Цена сделки

    @JsonProperty("q")
    private BigDecimal quantity; // Количество (объем)

    @JsonProperty("T")
    private long tradeTime; // Время сделки (UTC в миллисекундах)

    @JsonProperty("m")
    private boolean isMarketMaker; // Был ли покупатель маркетмейкером?

    @JsonProperty("M")
    private boolean ignore;
}

package io.cryptobot.market_data.depth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DepthSnapshotModel {

    @JsonProperty("lastUpdateId")
    private long lastUpdateId; // ID последнего обновления книги ордеров

    @JsonProperty("bids")
    private List<List<BigDecimal>> bids; // Лист цен покупки (bid price, quantity)

    @JsonProperty("asks")
    private List<List<BigDecimal>> asks; // Лист цен продажи (ask price, quantity)
}


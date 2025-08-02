package io.cryptobot.market_data.depth;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DepthUpdateModel {
    private String eventType; // Тип события ("depthUpdate")
    private long eventTime; // Время события (UTC в миллисекундах)
    private String symbol; // Символ (например, "BTCUSDT")
    private long firstUpdateId; // ID первого обновления
    private long finalUpdateId; // ID последнего обновления
    private List<List<BigDecimal>> bids; // Лист цен покупки (bid price, quantity)
    private List<List<BigDecimal>> asks; // Лист цен продажи (ask price, quantity)
}

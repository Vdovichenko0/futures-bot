package io.cryptobot.binance.trade.session.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Document(collection = "trade-session")
public class TradeSession {
    @Id
    private String id;
}

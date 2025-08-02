package io.cryptobot.market_data.depth;

import lombok.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DepthModel {
    private long lastUpdateId;
//    private final Map<Double, Double> bids = new ConcurrentHashMap<>();
//    private final Map<Double, Double> asks = new ConcurrentHashMap<>();

    // bids: по убыванию цены (лучший bid — первый)
    private final NavigableMap<BigDecimal, BigDecimal> bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    // asks: по возрастанию цены (лучший ask — первый)
    private final NavigableMap<BigDecimal, BigDecimal> asks = new ConcurrentSkipListMap<>();

    public void updateBids(Map<BigDecimal, BigDecimal> updates) {
        updates.forEach((price, quantity) -> {
            if (quantity == null || BigDecimal.ZERO.compareTo(quantity) == 0) {
                bids.remove(price);
            } else {
                bids.put(price, quantity);
            }
        });
    }

    public void updateAsks(Map<BigDecimal, BigDecimal> updates) {
        updates.forEach((price, quantity) -> {
            if (quantity == null || BigDecimal.ZERO.compareTo(quantity) == 0) {
                asks.remove(price);
            } else {
                asks.put(price, quantity);
            }
        });
    }
}


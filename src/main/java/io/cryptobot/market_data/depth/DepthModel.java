package io.cryptobot.market_data.depth;

import lombok.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DepthModel {
    private static final int MAX_LEVELS = 100;

    private long lastUpdateId;

    private final NavigableMap<BigDecimal, BigDecimal> bids = new ConcurrentSkipListMap<BigDecimal, BigDecimal>(Comparator.reverseOrder()) {
        @Override
        public BigDecimal put(BigDecimal key, BigDecimal value) {
            BigDecimal result = super.put(key, value);
            while (size() > MAX_LEVELS) {
                pollLastEntry();
            }
            return result;
        }
    };

    private final NavigableMap<BigDecimal, BigDecimal> asks = new ConcurrentSkipListMap<BigDecimal, BigDecimal>() {
        @Override
        public BigDecimal put(BigDecimal key, BigDecimal value) {
            BigDecimal result = super.put(key, value);
            while (size() > MAX_LEVELS) {
                pollLastEntry();
            }
            return result;
        }
    };

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
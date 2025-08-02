package io.cryptobot.ticker24h;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class Ticker24hMapper {

    public static Ticker24h from24hTicker(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }

        try {
            return Ticker24h.builder()
                    .coin(node.path("s").asText().toUpperCase()) // symbol field is "s" in WS event
                    .priceChange(getBigDecimal(node, "p"))
                    .priceChangePercent(getBigDecimal(node, "P"))
                    .weightedAvgPrice(getBigDecimal(node, "w"))
                    .lastPrice(getBigDecimal(node, "c"))
                    .lastQty(getBigDecimal(node, "Q"))
                    .openPrice(getBigDecimal(node, "o"))
                    .highPrice(getBigDecimal(node, "h"))
                    .lowPrice(getBigDecimal(node, "l"))
                    .volume(getBigDecimal(node, "v"))
                    .quoteVolume(getBigDecimal(node, "q"))
                    .openTime(getLong(node, "O"))
                    .closeTime(getLong(node, "C"))
                    .firstId(getLong(node, "F"))
                    .lastId(getLong(node, "L"))
                    .count(getLong(node, "n"))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal getBigDecimal(JsonNode node, String field) {
        if (!node.has(field)) return null;
        String text = node.get(field).asText();
        if (text == null || text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long getLong(JsonNode node, String field) {
        if (!node.has(field)) return null;
        try {
            return node.get(field).asLong();
        } catch (Exception e) {
            return null;
        }
    }
}

package io.cryptobot.market_data.aggTrade;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public class AggTradeMapper {
    public static AggTrade fromJson(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        try {
            return AggTrade.builder()
                    .symbol(node.path("s").asText(null))
                    .eventTime(node.path("E").asLong(0))
                    .aggregateTradeId(node.path("a").asLong(0))
                    .price(parseBigDecimal(node, "p"))
                    .quantity(parseBigDecimal(node, "q"))
                    .firstTradeId(node.path("f").asLong(0))
                    .lastTradeId(node.path("l").asLong(0))
                    .tradeTime(node.path("T").asLong(0))
                    .buyerIsMaker(node.path("m").asBoolean(false))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(JsonNode node, String field) {
        if (!node.has(field)) return null;
        String text = node.get(field).asText();
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package io.cryptobot.market_data.aggTrade;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AggTradeMapper {
    public static AggTrade fromJson(JsonNode node) { //WS
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

    public static List<AggTrade> fromRest(String symbol, JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<AggTrade> result = new ArrayList<>(root.size());
        for (JsonNode item : root) {
            long aggId      = item.path("a").asLong();
            String price    = item.path("p").asText();
            String qty      = item.path("q").asText();
            long firstId    = item.path("f").asLong();
            long lastId     = item.path("l").asLong();
            long ts         = item.path("T").asLong();
            boolean isMaker = item.path("m").asBoolean();

            AggTrade trade = AggTrade.builder()
                    .symbol(symbol.toUpperCase())
                    .aggregateTradeId(aggId)
                    .price(new BigDecimal(price))
                    .quantity(new BigDecimal(qty))
                    .firstTradeId(firstId)
                    .lastTradeId(lastId)
                    .tradeTime(ts)
                    .buyerIsMaker(isMaker)
                    .build();
            result.add(trade);
        }
        return result;
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

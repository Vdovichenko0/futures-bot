package io.cryptobot.market_data.depth;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public class DepthMapper {

    public static DepthUpdateModel fromJson(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }

        try {
            DepthUpdateModel model = new DepthUpdateModel();
            model.setEventType(node.path("e").asText(null));
            model.setEventTime(node.path("E").asLong(0));
            model.setSymbol(node.path("s").asText(null));
            model.setFirstUpdateId(node.path("U").asLong(0));
            model.setFinalUpdateId(node.path("u").asLong(0));

            // bids: array of [price, qty]
            if (node.has("b") && node.get("b").isArray()) {
                model.setBids(parseLevelArray(node.get("b")));
            }

            if (node.has("a") && node.get("a").isArray()) {
                model.setAsks(parseLevelArray(node.get("a")));
            }

            return model;
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.List<java.util.List<BigDecimal>> parseLevelArray(JsonNode arrayNode) {
        java.util.List<java.util.List<BigDecimal>> levels = new java.util.ArrayList<>();
        for (JsonNode level : arrayNode) {
            if (level.isArray() && level.size() >= 2) {
                BigDecimal price = new BigDecimal(level.get(0).asText());
                BigDecimal qty = new BigDecimal(level.get(1).asText());
                java.util.List<BigDecimal> entry = new java.util.ArrayList<>(2);
                entry.add(price);
                entry.add(qty);
                levels.add(entry);
            }
        }
        return levels;
    }
}
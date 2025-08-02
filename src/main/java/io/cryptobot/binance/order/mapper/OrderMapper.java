package io.cryptobot.binance.order.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import io.cryptobot.binance.order.model.Order;

import java.math.BigDecimal;

public class OrderMapper {
    public static Order fromRest(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        try {
            // REST не отдает некоторые динамические поля (как realizedPnl напрямую),
            // но мы заполняем то, что есть.
            return Order.builder()
                    .symbol(node.path("symbol").asText(null))
                    .clientOrderId(node.path("clientOrderId").asText(null))
                    .side(node.path("side").asText(null))
                    .orderType(node.path("type").asText(null))
                    .timeInForce(node.path("timeInForce").asText(null))
                    .quantity(parseBigDecimal(node, "origQty"))
                    .price(parseBigDecimal(node, "price"))
                    .averagePrice(parseBigDecimal(node, "avgPrice")) // в REST поле называется avgPrice
                    .lastFilledQty(parseBigDecimal(node, "executedQty"))
                    .cumulativeFilledQty(parseBigDecimal(node, "executedQty"))
                    .commission(null) // REST-ответ не содержит явной комиссии здесь
                    .commissionAsset(null)
                    .orderStatus(node.path("status").asText(null)) // FILLED, NEW и т.п.
                    .orderId(node.path("orderId").asLong(0))
                    .positionSide(node.path("positionSide").asText(null))
                    .reduceOnly(node.path("reduceOnly").asBoolean(false))
                    .originalType(node.path("origType").asText(null))
                    .workingType(node.path("workingType").asText(null))
                    .originalResponseType(node.path("selfTradePreventionMode").asText(null)) // V аналог
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    public static Order fromWS(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        try {
            return Order.builder()
                    .symbol(node.path("s").asText(null))
                    .clientOrderId(node.path("c").asText(null))
                    .side(node.path("S").asText(null))
                    .orderType(node.path("o").asText(null))
                    .timeInForce(node.path("f").asText(null))
                    .quantity(parseBigDecimal(node, "q"))
                    .price(parseBigDecimal(node, "p"))
                    .averagePrice(parseBigDecimal(node, "ap"))
                    .stopPrice(parseBigDecimal(node, "sp"))
                    .executionType(node.path("x").asText(null))
                    .orderStatus(node.path("X").asText(null))
                    .orderId(node.path("i").asLong(0))
                    .lastFilledQty(parseBigDecimal(node, "l"))
                    .cumulativeFilledQty(parseBigDecimal(node, "z"))
                    .lastFilledPrice(parseBigDecimal(node, "L"))
                    .commission(parseBigDecimal(node, "n"))
                    .commissionAsset(node.path("N").asText(null))
                    .tradeTime(node.path("T").asLong(0))
                    .tradeId(node.path("t").asLong(0))
                    .buyerIsMaker(node.path("m").asBoolean(false))
                    .reduceOnly(node.path("R").asBoolean(false))
                    .workingType(node.path("wt").asText(null))
                    .originalType(node.path("ot").asText(null))
                    .positionSide(node.path("ps").asText(null))
                    .closePosition(node.path("cp").asBoolean(false))
                    .realizedPnl(parseBigDecimal(node, "rp"))
                    .isPositionPnl(node.path("pP").asBoolean(false))
                    .sideEffectType(node.path("si").asInt(0))
                    .stopStatus(node.path("ss").asInt(0))
                    .originalResponseType(node.path("V").asText(null))
                    .positionMode(node.path("pm").asText(null))
                    .goodTillDate(node.path("gtd").asLong(0))
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

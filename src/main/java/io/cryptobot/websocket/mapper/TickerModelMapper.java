package io.cryptobot.websocket.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import io.cryptobot.websocket.model.TickerModel;

import java.math.BigDecimal;

public class TickerModelMapper {
    public static TickerModel mapFromRestApi(JsonNode json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        TickerModel ticker = new TickerModel();

        ticker.setEventType(safeGetText(json, "e"));
        ticker.setEventTime(safeGetLong(json, "E"));
        ticker.setSymbol(safeGetText(json, "s"));

        ticker.setPriceChange(safeParseBigDecimal(json, "p"));
        ticker.setPriceChangePct(safeParseBigDecimal(json, "P"));
        ticker.setWeightedAvg(safeParseBigDecimal(json, "w"));
        ticker.setFirstTradePrice(safeParseBigDecimal(json, "x"));
        ticker.setLastPrice(safeParseBigDecimal(json, "c"));
        ticker.setLastQty(safeParseBigDecimal(json, "Q"));
        ticker.setBidPrice(safeParseBigDecimal(json, "b"));
        ticker.setBidQty(safeParseBigDecimal(json, "B"));
        ticker.setAskPrice(safeParseBigDecimal(json, "a"));
        ticker.setAskQty(safeParseBigDecimal(json, "A"));
        ticker.setOpenPrice(safeParseBigDecimal(json, "o"));
        ticker.setHighPrice(safeParseBigDecimal(json, "h"));
        ticker.setLowPrice(safeParseBigDecimal(json, "l"));
        ticker.setVolume(safeParseBigDecimal(json, "v"));
        ticker.setQuoteVolume(safeParseBigDecimal(json, "q"));

        ticker.setStatisticsOpenTime(safeGetLong(json, "O"));
        ticker.setStatisticsCloseTime(safeGetLong(json, "C"));
        ticker.setFirstTradeId(safeGetLong(json, "F"));
        ticker.setLastTradeId(safeGetLong(json, "L"));
        ticker.setTotalTrades(safeGetLong(json, "n"));

        return ticker;
    }

    private static BigDecimal safeParseBigDecimal(JsonNode json, String fieldName) {
        if (json.has(fieldName) && !json.get(fieldName).isNull()) {
            String value = json.get(fieldName).asText();
            if (value != null && !value.trim().isEmpty()) {
                return new BigDecimal(value);
            }
        }
        return BigDecimal.ZERO;
    }

    private static String safeGetText(JsonNode json, String fieldName) {
        if (json.has(fieldName) && !json.get(fieldName).isNull()) {
            return json.get(fieldName).asText();
        }
        return "";
    }

    private static long safeGetLong(JsonNode json, String fieldName) {
        if (json.has(fieldName) && !json.get(fieldName).isNull()) {
            return json.get(fieldName).asLong();
        }
        return 0L;
    }
}

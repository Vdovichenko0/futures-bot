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

        ticker.setEventType(json.get("e").asText());
        ticker.setEventTime(json.get("E").asLong());
        ticker.setSymbol(json.get("s").asText());

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

        ticker.setStatisticsOpenTime(json.get("O").asLong());
        ticker.setStatisticsCloseTime(json.get("C").asLong());
        ticker.setFirstTradeId(json.get("F").asLong());
        ticker.setLastTradeId(json.get("L").asLong());
        ticker.setTotalTrades(json.get("n").asLong());

        return ticker;
    }

    private static BigDecimal safeParseBigDecimal(JsonNode json, String fieldName) {
        if (json.has(fieldName) && !json.get(fieldName).isNull()) {
            return new BigDecimal(json.get(fieldName).asText());
        }
        return BigDecimal.ZERO;
    }
}

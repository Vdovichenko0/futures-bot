package io.cryptobot.klines.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.klines.enums.IntervalE;
import io.cryptobot.klines.model.KlineModel;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class KlineMapper {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @NotNull
    public static KlineModel parseKlineFromWs(JsonNode jsonNode) {
        JsonNode klineNode = jsonNode.get("k");
        if (klineNode == null) {
            throw new IllegalArgumentException("Invalid Kline data from WebSocket");
        }
        
        KlineModel kline = new KlineModel();
        
        // Основные данные свечи
        kline.setOpenTime(klineNode.get("t").asLong());
        kline.setCloseTime(klineNode.get("T").asLong());
        kline.setSymbol(klineNode.get("s").asText());
        kline.setInterval(IntervalE.fromString(klineNode.get("i").asText()));
        
        // Ценовые данные
        kline.setOpenPrice(new BigDecimal(klineNode.get("o").asText()));
        kline.setClosePrice(new BigDecimal(klineNode.get("c").asText()));
        kline.setHighPrice(new BigDecimal(klineNode.get("h").asText()));
        kline.setLowPrice(new BigDecimal(klineNode.get("l").asText()));
        
        // Объемные данные
        kline.setVolume(new BigDecimal(klineNode.get("v").asText()));
        kline.setQuoteAssetVolume(new BigDecimal(klineNode.get("q").asText()));
        kline.setNumberOfTrades(klineNode.get("n").asLong());
        kline.setTakerBuyBaseVolume(new BigDecimal(klineNode.get("V").asText()));
        kline.setTakerBuyQuoteVolume(new BigDecimal(klineNode.get("Q").asText()));
        
        // Статус свечи
        kline.setClosed(klineNode.get("x").asBoolean());

        return kline;
    }
    
    @NotNull
    public static KlineModel parseKlineFromWs(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return parseKlineFromWs(jsonNode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON string: " + jsonString, e);
        }
    }

    @NotNull
    public static List<KlineModel> getKlineModels(String symbol, IntervalE interval, String jsonResponse) {
        try {
            JsonNode jsonArray = objectMapper.readTree(jsonResponse);
            List<KlineModel> klines = new ArrayList<>();
            
            for (JsonNode klineArray : jsonArray) {
                if (klineArray.isArray() && klineArray.size() >= 11) {
                    KlineModel kline = new KlineModel();
                    
                    // Парсим массив данных свечи
                    kline.setOpenTime(klineArray.get(0).asLong());
                    kline.setOpenPrice(new BigDecimal(klineArray.get(1).asText()));
                    kline.setHighPrice(new BigDecimal(klineArray.get(2).asText()));
                    kline.setLowPrice(new BigDecimal(klineArray.get(3).asText()));
                    kline.setClosePrice(new BigDecimal(klineArray.get(4).asText()));
                    kline.setVolume(new BigDecimal(klineArray.get(5).asText()));
                    kline.setCloseTime(klineArray.get(6).asLong());
                    kline.setQuoteAssetVolume(new BigDecimal(klineArray.get(7).asText()));
                    kline.setNumberOfTrades(klineArray.get(8).asLong());
                    kline.setTakerBuyBaseVolume(new BigDecimal(klineArray.get(9).asText()));
                    kline.setTakerBuyQuoteVolume(new BigDecimal(klineArray.get(10).asText()));
                    
                    // Устанавливаем символ и интервал
                    kline.setSymbol(symbol);
                    kline.setInterval(interval);
                    
                    // Свеча считается закрытой, если это исторические данные
                    kline.setClosed(true);
                    
                    klines.add(kline);
                }
            }
            
            return klines;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse klines from JSON: " + jsonResponse, e);
        }
    }

    public static long convertIntervalToMillis(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "3m" -> 180_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "30m" -> 1_800_000L;
            case "1h" -> 3_600_000L;
            case "2h" -> 7_200_000L;
            case "4h" -> 14_400_000L;
            case "6h" -> 21_600_000L;
            case "8h" -> 28_800_000L;
            case "12h" -> 43_200_000L;
            case "1d" -> 86_400_000L;
            case "1w" -> 604_800_000L;
            case "1M" -> 2_592_000_000L;
            default -> throw new IllegalArgumentException("Invalid interval: " + interval);
        };
    }
}

/*
[[1754089680000,"113229.50","113317.00","112500.10","113314.90","3.539",1754089739999,"399615.65490",323,"0.466","52767.31630","0"],[1754089740000,"113163.70","113319.00","113026.40","113318.90","4.026",1754089799999,"456210.60110",57,"4.018","455305.26500","0"],[1754089800000,"113318.90","113319.00","113289.30","113318.90","0.638",1754089859999,"72281.24700",85,"0.088","9972.06490","0"]]
 */
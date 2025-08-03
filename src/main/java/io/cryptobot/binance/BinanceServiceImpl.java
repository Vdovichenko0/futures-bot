package io.cryptobot.binance;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.binance.model.LeverageMarginInfo;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.configs.service.AppConfig;
import io.cryptobot.helpers.SymbolHelper;
import io.cryptobot.market_data.klines.enums.IntervalE;
import io.cryptobot.market_data.klines.mapper.KlineMapper;
import io.cryptobot.market_data.klines.model.KlineModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.LinkedHashMap;

import static io.cryptobot.configs.service.AppConfig.API_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceServiceImpl implements BinanceService {
    private final UMFuturesClientImpl umFuturesClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String BASE_URL_KLINES = "/fapi/v1/klines";
    private static final String BASE_URL_ACCOUNT = "/fapi/v2/account";

    @Scheduled(initialDelay = 30_000)
    public void init(){
//        getLeverageAndMarginMode("BTCUSDT");
//        setLeverage("BTCUSDT", 10);
//        setMarginType("BTCUSDT", true);
//        SizeModel sizeModel = SymbolHelper.getSizeModel("BTCUSDT");
    }

    @Override
    public LeverageMarginInfo getLeverageAndMarginMode(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol.toUpperCase());

            String result = umFuturesClient.account().positionInformation(params);
            JsonNode arrayNode = objectMapper.readTree(result);

            for (JsonNode node : arrayNode) {
                if (symbol.equalsIgnoreCase(node.get("symbol").asText()) && "LONG".equals(node.get("positionSide").asText())) {
                    int leverage = node.get("leverage").asInt();
                    boolean isolated = node.get("isolated").asBoolean();
                    return LeverageMarginInfo.builder()
                            .symbol(symbol.toUpperCase())
                            .leverage(leverage)
                            .isolated(isolated)
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get leverage/margin info for symbol: {}", symbol, e);
        }
        return null;
    }

    public boolean setLeverage(String symbol, int leverage) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol.toUpperCase());
            params.put("leverage", leverage);

            String response = umFuturesClient.account().changeInitialLeverage(params);
            log.info("✅ Leverage set: symbol={}, leverage={}, response={}", symbol, leverage, response);
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to set leverage: symbol={}, leverage={}", symbol, leverage, e);
            return false;
        }
    }

    @Override
    public boolean setMarginType(String symbol, boolean isolated) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol.toUpperCase());
            params.put("marginType", isolated ? "ISOLATED" : "CROSSED");

            String response = umFuturesClient.account().changeMarginType(params);

            log.info("✅ Margin type set: symbol={}, isolated={}, response={}", symbol, isolated, response);
            return true;

        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("\"code\":-4046")) {
                    log.info("✅ Margin type already set: symbol={}, isolated={}", symbol, isolated);
                    return true;
                }
                if (message.contains("\"code\":-4048")) {
                    log.warn("❌ Cannot change margin type: open position exists for symbol={}, isolated={}", symbol, isolated);
                    return false;
                }
            }
            log.error("❌ Failed to set margin type: symbol={}, isolated={}, error={}", symbol, isolated, message, e);
            return false;
        }
    }

    @Override
    public String getAccountInfo() {
        try {
            String url = AppConfig.BINANCE_URL + BASE_URL_ACCOUNT;

            log.info("Getting account info from: {}", url);

            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            String result = umFuturesClient.account().accountInformation(parameters);

            log.info("Account info retrieved successfully");
            return result;

        } catch (Exception e) {
            log.error("Failed to get account info", e);
            return "{\"error\": \"Failed to get account info: " + e.getMessage() + "\"}";
        }
    }

    @Override
    public List<KlineModel> getKlines(String symbol, IntervalE interval, int limit) {
        try {
            String url = AppConfig.BINANCE_URL + BASE_URL_KLINES;
            
            long endTime = System.currentTimeMillis();
            long intervalMillis = KlineMapper.convertIntervalToMillis(interval.getValue());
            long startTime = endTime - (intervalMillis * limit);

            String fullUrl = String.format("%s?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                    url, symbol.toUpperCase(), interval.getValue(), startTime, endTime, limit);

            log.info("Getting klines from: {}", fullUrl);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .GET();

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Binance Futures API error: HTTP {} - {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            log.debug("Binance Futures API response received, length: {}", response.body().length());
            
            List<KlineModel> klines = KlineMapper.getKlineModels(symbol, interval, response.body());

            log.info("Retrieved {} klines for {} {} from Binance Futures", klines.size(), symbol, interval.getValue());
            return klines;
            
        } catch (Exception e) {
            log.error("Unexpected error retrieving klines data from Binance Futures", e);
            return Collections.emptyList();
        }
    }

    public boolean testConnection() {
        try {
            String url = AppConfig.BINANCE_URL + "/fapi/v1/ping";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean isConnected = response.statusCode() == 200;
            log.info("Connection test to {}: {}", url, isConnected ? "SUCCESS" : "FAILED");
            
            return isConnected;
        } catch (Exception e) {
            log.error("Connection test failed", e);
            return false;
        }
    }
}
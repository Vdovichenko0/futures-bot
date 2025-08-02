package io.cryptobot.binance;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import io.cryptobot.configs.service.AppConfig;
import io.cryptobot.market_data.klines.enums.IntervalE;
import io.cryptobot.market_data.klines.mapper.KlineMapper;
import io.cryptobot.market_data.klines.model.KlineModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.LinkedHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceServiceImpl implements BinanceService {

    private final HttpClient httpClient;
    private static final String BASE_URL_KLINES = "/fapi/v1/klines";
    private static final String BASE_URL_ACCOUNT = "/fapi/v2/account";

    @Override
    public String getAccountInfo() {
        try {
            String url = AppConfig.BINANCE_URL + BASE_URL_ACCOUNT;

            log.info("Getting account info from: {}", url);

            UMFuturesClientImpl client = new UMFuturesClientImpl(AppConfig.API_KEY, AppConfig.SECRET_KEY, AppConfig.BINANCE_URL);

            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            String result = client.account().accountInformation(parameters);

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


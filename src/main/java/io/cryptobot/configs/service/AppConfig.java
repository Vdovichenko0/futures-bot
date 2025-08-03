package io.cryptobot.configs.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;

@Slf4j
@Component
@Getter
@RequiredArgsConstructor
public class AppConfig {

    @Value("${binance.url}")
    private String binanceUrl;

    @Value("${binance.url.ws}")
    private String binanceWSUrl;

    @Value("${api.key}")
    private String apiKey;
    @Value("${secret.key}")
    private String secretKey;

    public static String BINANCE_URL;
    public static String BINANCE_WS_URL;
    public static String API_KEY;
    public static String SECRET_KEY;

    @Bean
    public UMFuturesClientImpl umFuturesClient() {
        return new UMFuturesClientImpl(apiKey, secretKey, binanceUrl);
    }

    @PostConstruct
    public void init() {
        BINANCE_URL = binanceUrl;
        BINANCE_WS_URL = binanceWSUrl;
        API_KEY = apiKey;
        SECRET_KEY = secretKey;
        log.info("BINANCE_URL {}", BINANCE_URL);
        log.info("BINANCE_WS_URL {}", BINANCE_WS_URL);

        try {
            enableHedgeMode();
        } catch (Exception e) {
            log.error("Failed to initialize futures client or enable hedge model", e);
        }
    }

    private void enableHedgeMode() {
        try {
            log.info("🔄 Enabling hedge model (dual-side position)...");
            String resp;
            try {
                resp = setHedgeMode(true);
                log.info("✅ Hedge model enabled response: {}", resp);
            } catch (RuntimeException e) {
                if (e.getMessage().contains("\"code\":-4059")) {
                    log.info("✅ Hedge model already enabled (received -4059), continuing.");
                } else {
                    throw e;
                }
            }
            Thread.sleep(300);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting after enabling hedge model", ie);
        } catch (Exception e) {
            log.error("❌ Failed to enable hedge model", e);
        }
    }

    public String setHedgeMode(boolean enable) throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        String dualSideValue = enable ? "true" : "false";
        String queryString = "dualSidePosition=" + dualSideValue + "&timestamp=" + timestamp;

        String signature = hmacSHA256(queryString, AppConfig.SECRET_KEY);
        String fullPath = String.format("%s/fapi/v1/positionSide/dual?%s&signature=%s", AppConfig.BINANCE_URL, queryString, signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullPath))
                .header("X-MBX-APIKEY", AppConfig.API_KEY)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return resp.body();
        } else {
            throw new RuntimeException("Failed to set hedge model: " + resp.statusCode() + " " + resp.body());
        }
    }

    public String hmacSHA256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    public String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
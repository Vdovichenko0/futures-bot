package io.cryptobot.configs.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    @PostConstruct
    public void init() {
        BINANCE_URL = binanceUrl;
        BINANCE_WS_URL = binanceWSUrl;
        API_KEY = apiKey;
        SECRET_KEY = secretKey;
        log.info("BINANCE_URL {}", BINANCE_URL);
        log.info("BINANCE_WS_URL {}", BINANCE_WS_URL);
    }
}
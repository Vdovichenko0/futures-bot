package io.cryptobot.websocket;

import com.binance.connector.futures.client.WebsocketClient;
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import com.binance.connector.futures.client.utils.WebSocketCallback;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.configs.service.AppConfig;
import io.cryptobot.klines.mapper.KlineMapper;
import io.cryptobot.klines.model.KlineModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class BinanceWebSocketService {

    private static final String SYMBOL = "btcusdt";
    private static final String INTERVAL = "1m";
    private WebsocketClient wsClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void start() {
        executor.submit(this::connectWithRetry);
    }

    private void connectWithRetry() {
        while (running) {
            try {
                log.info("Attempting websocket connection to Binance Futures for {} kline {}", SYMBOL, INTERVAL);
                log.info("Using WebSocket URL: {}", AppConfig.BINANCE_WS_URL);
                
                // Создаем WebSocket клиент для USDT-M фьючерсов
                wsClient = new UMWebsocketClientImpl();
                
                WebSocketCallback callback = new WebSocketCallback() {
                    @Override
                    public void onReceive(String data) {
                        try {
                            log.debug("Received WebSocket data: {}", data);
                            
                            JsonNode jsonNode = objectMapper.readTree(data);
                            
                            if (jsonNode.has("e") && "kline".equals(jsonNode.get("e").asText())) {
                                KlineModel kline = KlineMapper.parseKlineFromWs(jsonNode);
//                                log.info("Processed kline for {}: Open={}, Close={}, Volume={}", kline.getSymbol(), kline.getOpenPrice(), kline.getClosePrice(), kline.getVolume());
                                
                                processKline(kline);
                            }
                        } catch (Exception e) {
                            log.error("Failed to process message from Binance WS: {}", data, e);
                        }
                    }
                };

                wsClient.klineStream(SYMBOL, INTERVAL, callback);
                
                log.info("Successfully connected to Binance WebSocket for kline data");
                
                while (running) {
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                log.error("WebSocket connection failed, will retry in 3s", e);
                sleepUninterruptibly(3);
            }
        }
    }

    private void processKline(KlineModel kline) {
        if (kline.isClosed()) {
//            log.info("Closed kline for {}: High={}, Low={}, Trades={}", kline.getSymbol(), kline.getHighPrice(), kline.getLowPrice(), kline.getNumberOfTrades());
        }
    }

    private void sleepUninterruptibly(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (wsClient != null) {
            wsClient.closeAllConnections();
        }
        executor.shutdownNow();
    }
}
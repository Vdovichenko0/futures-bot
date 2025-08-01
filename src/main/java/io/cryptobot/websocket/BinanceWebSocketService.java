package io.cryptobot.websocket;

import com.binance.connector.futures.client.WebsocketClient;
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import com.binance.connector.futures.client.utils.WebSocketCallback;
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

    @PostConstruct
    public void start() {
        executor.submit(this::connectWithRetry);
    }

    private void connectWithRetry() {
        while (running) {
            try {
                log.info("Attempting websocket connection to Binance Futures for {} kline {}", SYMBOL, INTERVAL);
                
                // Создаем WebSocket клиент для USDT-M фьючерсов
                wsClient = new UMWebsocketClientImpl();
                
                // Создаем callback для обработки сообщений
                WebSocketCallback callback = new WebSocketCallback() {
                    @Override
                    public void onReceive(String data) {
                        try {
                            log.info("Received kline data: {}", data);
                            // Здесь можно добавить парсинг JSON и обработку данных
                            // Библиотека уже предоставляет структурированные данные
                        } catch (Exception e) {
                            log.error("Failed to process message from Binance WS", e);
                        }
                    }
                };

                // Подписываемся на kline данные
                wsClient.klineStream(SYMBOL, INTERVAL, callback);
                
                // Держим соединение живым
                while (running) {
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                log.error("WebSocket connection failed, will retry in 3s", e);
                sleepUninterruptibly(3);
            }
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
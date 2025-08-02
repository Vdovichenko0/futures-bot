package io.cryptobot.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.binance.connector.futures.client.WebsocketClient;
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import com.binance.connector.futures.client.utils.WebSocketCallback;
import io.cryptobot.binance.order.mapper.OrderMapper;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.configs.service.AppConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceFuturesUserDataStreamService {

    private static final String LISTEN_KEY_ENDPOINT = "/fapi/v1/listenKey";
    private static final Duration KEEPALIVE_INTERVAL = Duration.ofMinutes(25);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService wsExecutor = Executors.newSingleThreadExecutor();

    private WebsocketClient websocketClient;
    private volatile String listenKey;
    private volatile int connectionId = -1;
    private final AtomicReference<Long> lastReceiveTime = new AtomicReference<>(0L);
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final OrderService orderService;

    public void start() {
        wsExecutor.submit(this::init);
    }

    private void init() {
        try {
            obtainListenKey();
            scheduleKeepAlive();
            connectUserStream();
        } catch (Exception e) {
            log.error("Failed to initialize user data stream", e);
        }
    }

    private void obtainListenKey() throws Exception {
        String url = AppConfig.BINANCE_URL + LISTEN_KEY_ENDPOINT;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-MBX-APIKEY", AppConfig.API_KEY)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(resp.body());
        if (node.has("listenKey")) {
            listenKey = node.get("listenKey").asText();
            log.info("Obtained listenKey for user data stream (testnet)");
        } else {
            throw new IllegalStateException("Failed to get listenKey: " + resp.body());
        }
    }

    private void scheduleKeepAlive() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                keepAliveListenKey();
            } catch (Exception e) {
                log.warn("Failed to keepalive listenKey, will try to re-obtain", e);
                try {
                    obtainListenKey();
                    reconnectWebsocket();
                } catch (Exception ex) {
                    log.error("Failed to recover listenKey", ex);
                }
            }
        }, KEEPALIVE_INTERVAL.toMillis(), KEEPALIVE_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void keepAliveListenKey() throws Exception {
        String url = AppConfig.BINANCE_URL + LISTEN_KEY_ENDPOINT;
        String body = "listenKey=" + listenKey;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-MBX-APIKEY", AppConfig.API_KEY)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("Keepalive responded non-200: {} body={}", resp.statusCode(), resp.body());
            throw new IllegalStateException("Keepalive failed");
        } else {
            log.debug("Refreshed listenKey successfully (testnet)");
        }
    }

    private void connectUserStream() {
        websocketClient = new UMWebsocketClientImpl(AppConfig.BINANCE_WS_URL);

        WebSocketCallback callback = new WebSocketCallback() {
            @Override
            public void onReceive(String data) {
                lastReceiveTime.set(System.currentTimeMillis());
                try {
                    JsonNode jsonNode = objectMapper.readTree(data);
                    String eventType = jsonNode.path("e").asText();

                    switch (eventType) {
                        case "ACCOUNT_UPDATE" -> handleAccountUpdate(jsonNode);
                        case "ORDER_TRADE_UPDATE" -> handleOrderTradeUpdate(jsonNode);
                        default -> log.debug("UserDataStream unknown event: {}", data);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse user data stream message: {}", data, e);
                }
            }
        };

        connectionId = websocketClient.listenUserStream(listenKey, callback);
        log.info("Connected to user data stream (testnet), connectionId={}", connectionId);
    }

    private void reconnectWebsocket() {
        if (websocketClient != null) {
            websocketClient.closeAllConnections();
        }
        connectUserStream();
    }

    private void handleAccountUpdate(JsonNode node) {
        JsonNode a = node.path("a");
//        log.info("ACCOUNT_UPDATE received: {}", a.toString());
    }

    private void handleOrderTradeUpdate(JsonNode node) {
        JsonNode o = node.path("o");
        Order update = OrderMapper.fromWS(o);
        if (update != null) {
//            log.info("ORDER_TRADE_UPDATE: {}", update);
            orderService.updateOrder(update);
        }
    }

    @PreDestroy
    public void shutdown() {
        cleanup();
    }

    private void cleanup() {
        try {
            if (websocketClient != null) {
                websocketClient.closeAllConnections();
            }
        } catch (Exception ignored) {
        }
        scheduler.shutdownNow();
        wsExecutor.shutdownNow();
    }
}

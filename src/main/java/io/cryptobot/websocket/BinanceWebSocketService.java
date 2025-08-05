package io.cryptobot.websocket;

import com.binance.connector.futures.client.WebsocketClient;
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import com.binance.connector.futures.client.utils.WebSocketCallback;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.configs.service.AppConfig;
import io.cryptobot.helpers.MainHelper;
import io.cryptobot.market_data.aggTrade.AggTrade;
import io.cryptobot.market_data.aggTrade.AggTradeMapper;
import io.cryptobot.market_data.aggTrade.AggTradeService;
import io.cryptobot.market_data.depth.DepthMapper;
import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.market_data.depth.DepthUpdateModel;
import io.cryptobot.market_data.klines.mapper.KlineMapper;
import io.cryptobot.market_data.klines.model.KlineModel;
import io.cryptobot.market_data.klines.service.KlineService;
import io.cryptobot.market_data.ticker24h.Ticker24h;
import io.cryptobot.market_data.ticker24h.Ticker24hMapper;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceWebSocketService {
    private static final String INTERVAL = "1m";
    private final MainHelper mainHelper;
    private WebsocketClient wsClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    private final KlineService klineService;
    private final Ticker24hService ticker24hService;
    private final AggTradeService aggTradeService;
    private final DepthService depthService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void start() {
        List<String> symbols = mainHelper.getSymbolsFromPlans();
        if (symbols.isEmpty()) {
            log.warn("No symbols found in trade plans, skipping WebSocket connections");
            return;
        }
        log.info("Starting WebSocket connections for symbols: {}", symbols);
        executor.submit(() -> connectWithRetry(symbols));
    }

    private void connectWithRetry(List<String> symbols) {
        while (running) {
            try {
                log.info("Attempting websocket connection to Binance Futures for {} symbols", symbols.size());
                log.info("Using WebSocket URL: {}", AppConfig.BINANCE_WS_URL);
                
                // Создаем WebSocket клиент для USDT-M фьючерсов
                wsClient = new UMWebsocketClientImpl(AppConfig.BINANCE_WS_URL);
                
                WebSocketCallback callback = data -> {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(data);

                        if (jsonNode.has("e")) {
//                            log.info(jsonNode.toString());
                            String event = jsonNode.get("e").asText();
                            if ("kline".equals(event)) {
                                KlineModel kline = KlineMapper.parseKlineFromWs(jsonNode);
                                if (kline.isClosed()) {
                                    klineService.addKline(kline);
                                }
                            } else if ("24hrTicker".equals(event)) {
                                Ticker24h ticker = Ticker24hMapper.from24hTicker(jsonNode);
                                if (ticker != null) {
                                    ticker24hService.addPrice(ticker);
                                }
                            }else if ("aggTrade".equals(jsonNode.path("e").asText())) {
                                AggTrade agg = AggTradeMapper.fromJson(jsonNode);
                                if (agg != null) {
                                    aggTradeService.addAggTrade(agg);
                                }
                            }else if ("depthUpdate".equals(jsonNode.path("e").asText())) {
                                DepthUpdateModel depthUpdate = DepthMapper.fromJson(jsonNode);
                                if (depthUpdate != null) {
//                                    log.info(depthUpdate.toString());
                                    depthService.processDepthUpdate(depthUpdate);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to process message from Binance WS: {}", data, e);
                    }
                };

                // Подключаемся ко всем символам
                for (String symbol : symbols) {
                    String formattedSymbol = symbol.toUpperCase();
                    try {
                        log.info("Connecting to streams for symbol: {}", formattedSymbol);
                        
                        wsClient.klineStream(formattedSymbol, INTERVAL, callback);
                        wsClient.symbolTicker(formattedSymbol, callback);
                        wsClient.aggTradeStream(formattedSymbol, callback);
                        wsClient.diffDepthStream(formattedSymbol, 500, callback); //100,250,500
                        
                        log.info("Successfully connected to streams for symbol: {}", formattedSymbol);
                    } catch (Exception e) {
                        log.error("Failed to connect to streams for symbol {}: {}", formattedSymbol, e.getMessage());
                    }
                }

                log.info("Successfully connected to Binance WebSocket for {} symbols", symbols.size());
                
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
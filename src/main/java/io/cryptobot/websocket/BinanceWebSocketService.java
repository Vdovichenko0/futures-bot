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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceWebSocketService {
    private static final String INTERVAL = "1m";
    private final MainHelper mainHelper;
    private WebsocketClient wsClient;
    private final CopyOnWriteArrayList<String> subscribed = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    private final KlineService klineService;
    private final Ticker24hService ticker24hService;
    private final AggTradeService aggTradeService;
    private final DepthService depthService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void start() {
        wsClient = new UMWebsocketClientImpl(AppConfig.BINANCE_WS_URL);
        subscribeAll(mainHelper.getSymbolsFromPlans());
    }

    /** Подписаться на один символ «на лету» */
    public void subscribeSymbol(String symbol) {
        String sym = symbol.toUpperCase();
        if (subscribed.contains(sym)) return;
        log.info("Subscribing dynamically to {}", sym);
        doSubscribe(sym);
        subscribed.add(sym);
    }

    /** Подписаться на пачку символов (стартап) */
    private void subscribeAll(List<String> symbols) {
        for (String s : symbols) {
            subscribeSymbol(s);
        }
    }

    /** Хук на низком уровне: дергаем все четыре стрима */
    private void doSubscribe(String sym) {
        WebSocketCallback callback = data -> {
            try {
                JsonNode json = objectMapper.readTree(data);
                String e = json.path("e").asText();
                switch (e) {
                    case "kline":
                        KlineModel kl = KlineMapper.parseKlineFromWs(json);
                        if (kl.isClosed()) klineService.addKline(kl);
                        break;
                    case "24hrTicker":
                        Ticker24h t = Ticker24hMapper.from24hTicker(json);
                        if (t != null) ticker24hService.addPrice(t);
                        break;
                    case "aggTrade":
                        AggTrade ag = AggTradeMapper.fromJson(json);
                        if (ag != null) aggTradeService.addAggTrade(ag);
                        break;
                    case "depthUpdate":
                        DepthUpdateModel du = DepthMapper.fromJson(json);
                        if (du != null) depthService.processDepthUpdate(du);
                        break;
                }
            } catch (Exception ex) {
                log.error("WS message processing error", ex);
            }
        };

        wsClient.klineStream(sym, INTERVAL, callback);
        wsClient.symbolTicker(sym, callback);
        wsClient.aggTradeStream(sym, callback);
        wsClient.diffDepthStream(sym, 500, callback);
        log.info("Streams opened for {}", sym);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (wsClient != null) wsClient.closeAllConnections();
    }
}
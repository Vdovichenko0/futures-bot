package io.cryptobot.utils;

import io.cryptobot.market_data.aggTrade.AggTradeService;
import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.market_data.klines.service.KlineService;
import io.cryptobot.websocket.BinanceWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataSubscriptionService {
    private final KlineService klineService;
    private final DepthService depthService;
    private final AggTradeService aggTradeService;
    private final BinanceWebSocketService websocketClient;

    public void subscribe(String symbol) {
        klineService.addNewKline(symbol);
        depthService.getDepthModelBySymbol(symbol);
        aggTradeService.addAggTradeREST(symbol);
        websocketClient.subscribeSymbol(symbol);
    }
}

package io.cryptobot.market_data.klines.service;


import io.cryptobot.market_data.klines.enums.IntervalE;
import io.cryptobot.market_data.klines.model.KlineModel;

import java.util.List;

public interface KlineService {

    void checkIndicators();

    void addKline(KlineModel kline);

    List<KlineModel> getKlines(String symbol, IntervalE interval);

//    KlinesMSM buildKlineMSM(String symbol);

//    MarketPhase getMarketPhase(String symbol, IntervalE intervalE);

    void clearKlines(String symbol, IntervalE interval);

    KlineModel getLatestKline(String symbol, IntervalE interval);

    void fetchInitialKlines();

    void addNewKline(String coin);

}

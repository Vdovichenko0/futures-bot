package io.cryptobot.binance;

import io.cryptobot.binance.model.LeverageMarginInfo;
import io.cryptobot.market_data.klines.enums.IntervalE;
import io.cryptobot.market_data.klines.model.KlineModel;

import java.util.List;

public interface BinanceService {
    LeverageMarginInfo getLeverageAndMarginMode(String symbol);

    boolean setLeverage(String symbol, int leverage);

    boolean setMarginType(String symbol, boolean isolated);

    String getAccountInfo();

    List<KlineModel> getKlines(String symbol, IntervalE interval, int limit);
}


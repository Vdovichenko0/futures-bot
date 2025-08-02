package io.cryptobot.binance;

import io.cryptobot.klines.enums.IntervalE;
import io.cryptobot.klines.model.KlineModel;

import java.util.List;

public interface BinanceService {
    String getAccountInfo();

    List<KlineModel> getKlines(String symbol, IntervalE interval, int limit);
}


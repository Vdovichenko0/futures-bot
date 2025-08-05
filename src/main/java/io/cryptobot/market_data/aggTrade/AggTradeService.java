package io.cryptobot.market_data.aggTrade;

import java.util.List;

public interface AggTradeService {

    void addAggTrade(AggTrade aggTrade);

    AggTrade getAggTrade(String coin);

    List<AggTrade> getRecentTrades(String coin, int limit);
}

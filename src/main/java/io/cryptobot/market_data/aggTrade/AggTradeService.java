package io.cryptobot.market_data.aggTrade;

import java.util.List;

public interface AggTradeService {

    void addAggTrade(AggTrade aggTrade);

    List<AggTrade> getRecentTrades(String coin, int limit);

    void addAggTradeREST(String coin);
}

package io.cryptobot.market_data.aggTrade;

import java.util.Deque;

public interface AggTradeService {

    void addAggTrade(AggTrade aggTrade);

    Deque<AggTrade> getRecentTradesDeque(String coin);

    void addAggTradeREST(String coin);
}

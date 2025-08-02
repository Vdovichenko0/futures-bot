package io.cryptobot.market_data.aggTrade;

public interface AggTradeService {

    void addAggTrade(AggTrade aggTrade);

    AggTrade getAggTrade(String coin);
}

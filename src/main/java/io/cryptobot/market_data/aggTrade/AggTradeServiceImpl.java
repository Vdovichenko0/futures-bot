package io.cryptobot.market_data.aggTrade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggTradeServiceImpl implements AggTradeService{
    private final ConcurrentHashMap<String, AggTrade> aggTrades = new ConcurrentHashMap<>();

    @Override
    public void addAggTrade(AggTrade aggTrade) {
        aggTrades.put(aggTrade.getSymbol().toUpperCase(), aggTrade);

    }

    @Override
    public AggTrade getAggTrade(String coin) {
        if (coin == null) {
            return null;
        }
        return aggTrades.get(coin.toUpperCase());
    }
}

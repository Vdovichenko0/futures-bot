package io.cryptobot.market_data.aggTrade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggTradeServiceImpl implements AggTradeService{
    private final ConcurrentHashMap<String, List<AggTrade>> aggTrades = new ConcurrentHashMap<>();
    private static final int MAX_TRADES = 3600;

    @Override
    public void addAggTrade(AggTrade aggTrade) {
        String symbol = aggTrade.getSymbol().toUpperCase();
        List<AggTrade> list = aggTrades.computeIfAbsent(symbol,
                k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            if (list.size() == MAX_TRADES) {
                list.remove(0);
            }
            list.add(aggTrade);
        }
    }

    @Override
    public AggTrade getAggTrade(String coin) {
        List<AggTrade> list = aggTrades.get(coin.toUpperCase());
        if (list == null || list.isEmpty()) return null;
        synchronized (list) {
            return list.get(list.size() - 1);
        }
    }

    @Override
    public List<AggTrade> getRecentTrades(String coin, int limit) {
        List<AggTrade> list = aggTrades.get(coin.toUpperCase());
        if (list == null) return List.of();
        synchronized (list) {
            int from = Math.max(0, list.size() - limit);
            return new ArrayList<>(list.subList(from, list.size()));
        }
    }
}

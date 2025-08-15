package io.cryptobot.market_data.aggTrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.configs.service.AppConfig;
import io.cryptobot.helpers.MainHelper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggTradeServiceImpl implements AggTradeService{
    private final ConcurrentHashMap<String, Deque<AggTrade>> aggTrades = new ConcurrentHashMap<>();
    private final MainHelper mainHelper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private static final String AGG_TRADES_ENDPOINT = "/fapi/v1/aggTrades";
    private static final int MAX_TRADES = 3600;

    @Override
    public void addAggTrade(AggTrade aggTrade) {
        String symbol = aggTrade.getSymbol().toUpperCase();
        Deque<AggTrade> deque = aggTrades.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addFirst(aggTrade);
            if (deque.size() > MAX_TRADES) {
                deque.removeLast();
            }
        }
    }

    @Override
    public Deque<AggTrade> getRecentTradesDeque(String coin) {
        Deque<AggTrade> dq = aggTrades.get(coin.toUpperCase());
        if (dq == null) return new ArrayDeque<>();
        synchronized (dq) {
            return new ArrayDeque<>(dq); // snapshot: head=newest, tail=oldest
        }
    }

//    @Override
//    public List<AggTrade> getRecentTrades(String coin, int limit) {
//        Deque<AggTrade> deque = aggTrades.get(coin.toUpperCase());
//        if (deque == null) return List.of();
//        synchronized (deque) {
//            Iterator<AggTrade> it = deque.iterator();
//            List<AggTrade> slice = new ArrayList<>(limit);
//            for (int i = 0; i < limit && it.hasNext(); i++) {
//                slice.add(it.next());
//            }
//            return slice;
//        }
//    }

    @PostConstruct
    public void initialize() {
        List<String> symbols = mainHelper.getSymbolsFromPlans();
        for (String symbol : symbols) {
            addAggTradeREST(symbol);
        }
        log.info("Initialized orderBooks for {} symbols", symbols.size());
    }

    @Override
    public void addAggTradeREST(String coin) {
        String url = AppConfig.BINANCE_URL + AGG_TRADES_ENDPOINT + "?symbol=" + coin + "&limit=1000";

        try {
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(resp);
            List<AggTrade> trades = AggTradeMapper.fromRest(coin, root);
            trades.sort(Comparator
                    .comparingLong(AggTrade::getTradeTime)
                    .thenComparingLong(AggTrade::getAggregateTradeId));

            for (AggTrade t : trades) {
                addAggTrade(t);
            }
            log.info("Loaded {} aggTrades via REST for {}", trades.size(), coin);
        } catch (Exception ex) {
            log.error("Failed to load aggTrades via REST for {}: {}", coin, ex.getMessage(), ex);
        }
    }
}

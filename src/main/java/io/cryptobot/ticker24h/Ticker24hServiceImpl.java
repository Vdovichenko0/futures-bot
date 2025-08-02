package io.cryptobot.ticker24h;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ticker24hServiceImpl implements Ticker24hService {
    private final ConcurrentHashMap<String, Ticker24h> lastPrices = new ConcurrentHashMap<>();

    @Override
    public void addPrice(Ticker24h ticker24h) {
    lastPrices.put(ticker24h.getCoin().toUpperCase(), ticker24h);
    }

    @Override
    public BigDecimal getPrice(String coin) {
        Ticker24h ticker = getTicker(coin.toUpperCase());
        return ticker != null ? ticker.getLastPrice() : null;
    }

    @Override
    public Ticker24h getTicker(String coin) {
        if (coin == null) {
            return null;
        }
        return lastPrices.get(coin.toUpperCase());
    }
}

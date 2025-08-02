package io.cryptobot.ticker24h;

import java.math.BigDecimal;

public interface Ticker24hService {
    void addPrice(Ticker24h ticker24h);

    BigDecimal getPrice(String coin);

    Ticker24h getTicker(String coin);
}

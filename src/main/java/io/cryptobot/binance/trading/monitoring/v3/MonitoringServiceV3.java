package io.cryptobot.binance.trading.monitoring.v3;

import io.cryptobot.binance.trade.session.model.TradeSession;

public interface MonitoringServiceV3 {
    void addToMonitoring(TradeSession tradeSession);

}

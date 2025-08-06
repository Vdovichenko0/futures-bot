package io.cryptobot.binance.trading.monitoring;

import io.cryptobot.binance.trade.session.model.TradeSession;

public interface MonitoringService {
    void addToMonitoring(TradeSession tradeSession);
}

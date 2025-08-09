package io.cryptobot.binance.trading.monitoring.v2;

import io.cryptobot.binance.trade.session.model.TradeSession;

public interface MonitoringServiceV2 {
    void addToMonitoring(TradeSession tradeSession);
}

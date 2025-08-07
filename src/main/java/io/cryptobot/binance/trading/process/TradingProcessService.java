package io.cryptobot.binance.trading.process;

import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;

import java.math.BigDecimal;

public interface TradingProcessService {

    void openOrder(TradePlan plan, TradingDirection direction, BigDecimal currentPrice, String context);

}

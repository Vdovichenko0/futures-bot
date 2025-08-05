package io.cryptobot.binance.trade.trade_plan.service.get;

import io.cryptobot.binance.trade.trade_plan.model.TradePlan;

import java.util.List;

public interface TradePlanGetService {
    TradePlan getPlan(String symbol);

    List<TradePlan> getAll();

    List<TradePlan> getAllActiveTrue();

    List<TradePlan> getAllActiveFalse();
}

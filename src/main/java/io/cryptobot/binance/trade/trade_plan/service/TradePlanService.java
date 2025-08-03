package io.cryptobot.binance.trade.trade_plan.service;

import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;

public interface TradePlanService {
    TradePlan createPlan(TradePlanCreateDto dto);
}
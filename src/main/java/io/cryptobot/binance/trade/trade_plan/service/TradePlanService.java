package io.cryptobot.binance.trade.trade_plan.service;

import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;

import java.util.List;

public interface TradePlanService {
    TradePlan createPlan(TradePlanCreateDto dto);

    List<String> createManyPlans(List<TradePlanCreateDto> dtos);

    void startSession(String coin, String context, TradingDirection direction);
}
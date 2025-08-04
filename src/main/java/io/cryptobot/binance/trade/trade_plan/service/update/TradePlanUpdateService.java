package io.cryptobot.binance.trade.trade_plan.service.update;

import io.cryptobot.binance.trade.trade_plan.model.TradePlan;

import java.math.BigDecimal;

public interface TradePlanUpdateService {
    TradePlan updateLeverage(String idPlan, int leverage);

    TradePlan updateAmount(String idPlan, BigDecimal amount);

    void addProfit(String idPlan, BigDecimal profit);

    void openPlan(String idPlan);

    void closePlan(String idPlan);

    void setActiveTrue(String idPlan, String idNewSession);

    void setActiveTrueFalse(String idPlan);

    TradePlan updateImbalance(String idPlan,BigDecimal imb);

    TradePlan updateRatio(String idPlan, BigDecimal ratio);

    void scheduledUpdateSizes();

    void scheduledSendRequestUpdateLeverage();

}

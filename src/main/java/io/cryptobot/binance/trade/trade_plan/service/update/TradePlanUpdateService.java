package io.cryptobot.binance.trade.trade_plan.service.update;

import io.cryptobot.binance.trade.trade_plan.dto.TradeMetricsDto;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;

import java.math.BigDecimal;

public interface TradePlanUpdateService {
    TradePlan updateLeverage(String idPlan, int leverage);

    TradePlan updateAmount(String idPlan, BigDecimal amount);

    TradePlan updateMetrics(String idPlan, TradeMetricsDto dto);

    void addProfit(String idPlan, BigDecimal profit);

    void openPlan(String idPlan);

    void closePlan(String idPlan);

    void setActiveTrue(String idPlan, String idNewSession);

    void setActiveTrueFalse(String idPlan);

    void scheduledUpdateSizes();

    void scheduledSendRequestUpdateLeverage();

}

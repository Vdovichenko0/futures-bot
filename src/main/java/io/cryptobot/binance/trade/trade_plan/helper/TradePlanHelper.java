package io.cryptobot.binance.trade.trade_plan.helper;

import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;

import java.math.BigDecimal;

public class TradePlanHelper {

    public static void validateCreatePlan(TradePlanCreateDto dto) {
        // symbol not null - size 4-25
        // metrics - imbalance - -100/100 longShortRatio - -100/100
        // amountPerTrade - 3-10000 (our amount)
        // leverage - 1-125

        if (dto == null) {
            throw new IllegalArgumentException("TradePlanCreateDto cannot be null");
        }

        // Validate symbol
        validateSymbol(dto.getSymbol());

        // Validate metrics
        validateMetrics(dto.getMetrics());

        // Validate amountPerTrade
        validateAmountPerTrade(dto.getAmountPerTrade());

        // Validate leverage
        validateLeverage(dto.getLeverage());
    }

    private static void validateSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        if (symbol.length() < 4 || symbol.length() > 25) {
            throw new IllegalArgumentException("Symbol length must be between 4 and 25 characters");
        }
    }

    private static void validateMetrics(TradeMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("TradeMetrics cannot be null");
        }

        // Validate imbalance (-100 to 100)
        validateMetricValue(metrics.getImbalance(), "imbalance", -100, 100);

        // Validate longShortRatio (-100 to 100)
        validateMetricValue(metrics.getLongShortRatio(), "longShortRatio", -100, 100);
    }

    private static void validateMetricValue(BigDecimal value, String metricName, int min, int max) {
        if (value == null) {
            throw new IllegalArgumentException(metricName + " cannot be null");
        }

        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException(metricName + " must be between " + min + " and " + max);
        }
    }

    private static void validateAmountPerTrade(BigDecimal amountPerTrade) {
        if (amountPerTrade == null) {
            throw new IllegalArgumentException("AmountPerTrade cannot be null");
        }

        if (amountPerTrade.compareTo(BigDecimal.valueOf(3)) < 0 || 
            amountPerTrade.compareTo(BigDecimal.valueOf(10000)) > 0) {
            throw new IllegalArgumentException("AmountPerTrade must be between 3 and 10000");
        }
    }

    private static void validateLeverage(int leverage) {
        if (leverage < 1 || leverage > 125) {
            throw new IllegalArgumentException("Leverage must be between 1 and 125");
        }
    }
}

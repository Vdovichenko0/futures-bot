package io.cryptobot.binance.trade.trade_plan.helper;

import io.cryptobot.binance.trade.trade_plan.dto.TradeMetricsDto;
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

    public static void validateSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        if (symbol.length() < 4 || symbol.length() > 25) {
            throw new IllegalArgumentException("Symbol length must be between 4 and 25 characters");
        }
    }

    public static void validateMetrics(TradeMetricsDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("TradeMetricsDto cannot be null");
        }

        validatePercentage(dto.getMinLongPct(), "minLongPct");
        validatePercentage(dto.getMinShortPct(), "minShortPct");

        validateRatioRange(dto.getMinImbalanceLong(), 0.0, 1.0, "minImbalanceLong");
        validateRatioRange(dto.getMaxImbalanceShort(), 0.0, 1.0, "maxImbalanceShort");

        validatePositiveDouble(dto.getEmaSensitivity(), "emaSensitivity", 0.0, 0.1);
        validatePositiveDouble(dto.getVolRatioThreshold(), "volRatioThreshold", 0.0, 100.0);

        validatePositiveInt(dto.getVolWindowSec(), "volWindowSec", 1, 600);
        validatePositiveInt(dto.getDepthLevels(), "depthLevels", 1, 500);
    }

    public static void validatePercentage(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }


    public static void validateRatioRange(double value, double min, double max, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }

    public static void validatePositiveDouble(double value, String name, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }

    public static void validatePositiveInt(int value, String name, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }

    public static void validateAmountPerTrade(BigDecimal amountPerTrade) {
        if (amountPerTrade == null) {
            throw new IllegalArgumentException("AmountPerTrade cannot be null");
        }

        if (amountPerTrade.compareTo(BigDecimal.valueOf(3)) < 0 || 
            amountPerTrade.compareTo(BigDecimal.valueOf(10000)) > 0) {
            throw new IllegalArgumentException("AmountPerTrade must be between 3 and 10000");
        }
    }

    public static void validateLeverage(int leverage) {
        if (leverage < 1 || leverage > 125) {
            throw new IllegalArgumentException("Leverage must be between 1 and 125");
        }
    }
}

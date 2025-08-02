package io.cryptobot.helpers;

import io.cryptobot.binance.order.model.Order;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class OrderHelper {

    /**
     * Сливает изменения из updated в existing и возвращает true если были изменения
     */
    public static boolean mergeAndDetectChanges(Order existing, Order updated, StringBuilder changes) {
        boolean changed = false;

        // Строковые поля
        changed |= setIfDifferent(existing::getSymbol, existing::setSymbol, updated.getSymbol(), "symbol", changes);
        changed |= setIfDifferent(existing::getClientOrderId, existing::setClientOrderId, updated.getClientOrderId(), "clientOrderId", changes);
        changed |= setIfDifferent(existing::getSide, existing::setSide, updated.getSide(), "side", changes);
        changed |= setIfDifferent(existing::getOrderType, existing::setOrderType, updated.getOrderType(), "orderType", changes);
        changed |= setIfDifferent(existing::getTimeInForce, existing::setTimeInForce, updated.getTimeInForce(), "timeInForce", changes);
        changed |= setIfDifferent(existing::getExecutionType, existing::setExecutionType, updated.getExecutionType(), "executionType", changes);
        changed |= setIfDifferent(existing::getOrderStatus, existing::setOrderStatus, updated.getOrderStatus(), "orderStatus", changes);
        changed |= setIfDifferent(existing::getCommissionAsset, existing::setCommissionAsset, updated.getCommissionAsset(), "commissionAsset", changes);
        changed |= setIfDifferent(existing::getWorkingType, existing::setWorkingType, updated.getWorkingType(), "workingType", changes);
        changed |= setIfDifferent(existing::getOriginalType, existing::setOriginalType, updated.getOriginalType(), "originalType", changes);
        changed |= setIfDifferent(existing::getPositionSide, existing::setPositionSide, updated.getPositionSide(), "positionSide", changes);
        changed |= setIfDifferent(existing::getOriginalResponseType, existing::setOriginalResponseType, updated.getOriginalResponseType(), "originalResponseType", changes);
        changed |= setIfDifferent(existing::getPositionMode, existing::setPositionMode, updated.getPositionMode(), "positionMode", changes);

        // BigDecimal поля
        changed |= setIfDifferent(existing::getQuantity, existing::setQuantity, updated.getQuantity(), "quantity", changes);
        changed |= setIfDifferent(existing::getPrice, existing::setPrice, updated.getPrice(), "price", changes);
        changed |= setIfDifferent(existing::getAveragePrice, existing::setAveragePrice, updated.getAveragePrice(), "averagePrice", changes);
        changed |= setIfDifferent(existing::getStopPrice, existing::setStopPrice, updated.getStopPrice(), "stopPrice", changes);
        changed |= setIfDifferent(existing::getLastFilledQty, existing::setLastFilledQty, updated.getLastFilledQty(), "lastFilledQty", changes);
        changed |= setIfDifferent(existing::getCumulativeFilledQty, existing::setCumulativeFilledQty, updated.getCumulativeFilledQty(), "cumulativeFilledQty", changes);
        changed |= setIfDifferent(existing::getLastFilledPrice, existing::setLastFilledPrice, updated.getLastFilledPrice(), "lastFilledPrice", changes);
        changed |= setIfDifferent(existing::getCommission, existing::setCommission, updated.getCommission(), "commission", changes);
        changed |= setIfDifferent(existing::getRealizedPnl, existing::setRealizedPnl, updated.getRealizedPnl(), "realizedPnl", changes);

        // Примитивные поля
        if (existing.getTradeTime() != updated.getTradeTime()) {
            existing.setTradeTime(updated.getTradeTime());
            changes.append("tradeTime, ");
            changed = true;
        }
        if (existing.getTradeId() != updated.getTradeId()) {
            existing.setTradeId(updated.getTradeId());
            changes.append("tradeId, ");
            changed = true;
        }
        if (existing.isBuyerIsMaker() != updated.isBuyerIsMaker()) {
            existing.setBuyerIsMaker(updated.isBuyerIsMaker());
            changes.append("buyerIsMaker, ");
            changed = true;
        }
        if (existing.isReduceOnly() != updated.isReduceOnly()) {
            existing.setReduceOnly(updated.isReduceOnly());
            changes.append("reduceOnly, ");
            changed = true;
        }
        if (existing.isClosePosition() != updated.isClosePosition()) {
            existing.setClosePosition(updated.isClosePosition());
            changes.append("closePosition, ");
            changed = true;
        }
        if (existing.isPositionPnl() != updated.isPositionPnl()) {
            existing.setPositionPnl(updated.isPositionPnl());
            changes.append("isPositionPnl, ");
            changed = true;
        }
        if (existing.getSideEffectType() != updated.getSideEffectType()) {
            existing.setSideEffectType(updated.getSideEffectType());
            changes.append("sideEffectType, ");
            changed = true;
        }
        if (existing.getStopStatus() != updated.getStopStatus()) {
            existing.setStopStatus(updated.getStopStatus());
            changes.append("stopStatus, ");
            changed = true;
        }
        if (existing.getGoodTillDate() != updated.getGoodTillDate()) {
            existing.setGoodTillDate(updated.getGoodTillDate());
            changes.append("goodTillDate, ");
            changed = true;
        }

        return changed;
    }

    /**
     * Сравнивает и устанавливает строковое значение если оно отличается
     */
    private static boolean setIfDifferent(Supplier<String> getter, Consumer<String> setter, String newValue, String fieldName, StringBuilder log) {
        if (!java.util.Objects.equals(getter.get(), newValue)) {
            setter.accept(newValue);
            log.append(fieldName).append(", ");
            return true;
        }
        return false;
    }

    /**
     * Сравнивает и устанавливает BigDecimal значение если оно отличается
     */
    private static boolean setIfDifferent(Supplier<BigDecimal> getter, Consumer<BigDecimal> setter, BigDecimal newValue, String fieldName, StringBuilder log) {
        if (compareBigDecimals(getter.get(), newValue) != 0) {
            setter.accept(newValue);
            log.append(fieldName).append(", ");
            return true;
        }
        return false;
    }

    /**
     * Сравнивает два BigDecimal с учетом null значений
     */
    private static int compareBigDecimals(BigDecimal a, BigDecimal b) {
        BigDecimal valueA = (a != null) ? normalize(a) : BigDecimal.ZERO;
        BigDecimal valueB = (b != null) ? normalize(b) : BigDecimal.ZERO;
        return valueA.compareTo(valueB);
    }

    /**
     * Нормализует BigDecimal, убирая trailing zeros
     */
    private static BigDecimal normalize(BigDecimal v) {
        return v != null ? v.stripTrailingZeros() : BigDecimal.ZERO;
    }
} 
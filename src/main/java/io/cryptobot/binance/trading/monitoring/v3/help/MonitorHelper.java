package io.cryptobot.binance.trading.monitoring.v3.help;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


@Slf4j
@Component
public class MonitorHelper {
    public TradeOrder getActiveOrderForMonitoring(TradeSession session) {
        boolean activeLong = session.isActiveLong();
        boolean activeShort = session.isActiveShort();
        if (!activeLong && !activeShort) return null;
        if (activeLong && !activeShort) return getLatestActiveOrderByDirection(session, TradingDirection.LONG);
        if (activeShort && !activeLong) return getLatestActiveOrderByDirection(session, TradingDirection.SHORT);
        return session.getMainOrder(); // обе активны — основной для расчёта single ветки не используется
    }

    public TradeOrder getLatestActiveOrderByDirection(TradeSession session, TradingDirection dir) {
        // берём все FILLED/NEW OPEN-ордеры этой стороны
        List<TradeOrder> opens = session.getOrders().stream()
                .filter(o -> o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW)
                .filter(o -> dir.equals(o.getDirection()))
                .filter(o -> o.getPurpose() == OrderPurpose.MAIN_OPEN || o.getPurpose() == OrderPurpose.HEDGE_OPEN)
                .toList();

        // отделяем MAIN и HEDGE
        Optional<TradeOrder> mainOpen = opens.stream()
                .filter(o -> o.getPurpose() == OrderPurpose.MAIN_OPEN)
                .filter(o -> !isOpenOrderClosed(session, o))
                .max(Comparator.comparing(TradeOrder::getOrderTime));

        if (mainOpen.isPresent()) {
            return mainOpen.get(); // MAIN ещё активен — приоритетно возвращаем его
        }

        // MAIN закрыт — ищем последний активный HEDGE
        return opens.stream()
                .filter(o -> o.getPurpose() == OrderPurpose.HEDGE_OPEN)
                .filter(o -> !isOpenOrderClosed(session, o))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    public TradeOrder getLastFilledHedgeOrderByDirection(TradeSession session, TradingDirection dir) {
        return session.getOrders().stream()
                .filter(o -> OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .filter(o -> dir.equals(o.getDirection()))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    public boolean isOpenOrderClosed(TradeSession session, TradeOrder open) {
        if (OrderPurpose.MAIN_OPEN.equals(open.getPurpose())) {
            return session.getOrders().stream()
                    .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                    .anyMatch(o -> (OrderPurpose.MAIN_CLOSE.equals(o.getPurpose()) || OrderPurpose.MAIN_PARTIAL_CLOSE.equals(o.getPurpose()))
                            && open.getOrderId().equals(o.getParentOrderId()));
        }
        if (OrderPurpose.HEDGE_OPEN.equals(open.getPurpose())) {
            return session.getOrders().stream()
                    .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                    .anyMatch(o -> (OrderPurpose.HEDGE_CLOSE.equals(o.getPurpose()) || OrderPurpose.HEDGE_PARTIAL_CLOSE.equals(o.getPurpose()))
                            && open.getOrderId().equals(o.getParentOrderId()));
        }
        return false;
    }

    public boolean isMainStillActive(TradeSession session) {
        TradeOrder main = session.getMainOrder();
        if (main == null) return false;
        if (main.getDirection() == TradingDirection.LONG && !session.isActiveLong()) return false;
        if (main.getDirection() == TradingDirection.SHORT && !session.isActiveShort()) return false;

        boolean hasMainClose = session.getOrders().stream()
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .anyMatch(o -> (OrderPurpose.MAIN_CLOSE.equals(o.getPurpose()) || OrderPurpose.MAIN_PARTIAL_CLOSE.equals(o.getPurpose()))
                        && main.getOrderId().equals(o.getParentOrderId()));
        return !hasMainClose;
    }

    public boolean isDirectionActiveByOrders(TradeSession s, TradingDirection dir) {
        return s.getOrders().stream()
                .filter(o -> o.getPurpose() == OrderPurpose.MAIN_OPEN || o.getPurpose() == OrderPurpose.HEDGE_OPEN)
                .filter(o -> o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW)
                .filter(o -> dir.equals(o.getDirection()))
                .anyMatch(o -> !isOpenOrderClosed(s, o));
    }

    public boolean isDirectionActive(TradeSession s, TradingDirection dir) {
        boolean flag = (dir == TradingDirection.LONG) ? s.isActiveLong() : s.isActiveShort();
        return flag || isDirectionActiveByOrders(s, dir);
    }

    public OrderPurpose determineCloseOrderPurpose(TradeOrder o) {
        if (o.getPurpose() == OrderPurpose.MAIN_OPEN) return OrderPurpose.MAIN_CLOSE;
        if (o.getPurpose() == OrderPurpose.HEDGE_OPEN) return OrderPurpose.HEDGE_CLOSE;
        log.warn("⚠️ Unknown purpose {}, fallback HEDGE_CLOSE", o.getPurpose());
        return OrderPurpose.HEDGE_CLOSE;
    }

    public String inflightKey(String sessionId, TradingDirection dir) {
        return sessionId + "|" + dir.name();
    }

    public boolean isValidOrder(TradeOrder order) {
        return order != null
                && order.getOrderId() != null
                && order.getDirection() != null
                && order.getPurpose() != null
                && order.getPrice() != null
                && order.getPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isValidForClosing(TradeOrder order) {
        if (!isValidOrder(order)) return false;
        return order.getPurpose() == OrderPurpose.MAIN_OPEN || order.getPurpose() == OrderPurpose.HEDGE_OPEN;
    }

    public boolean isSessionInValidState(TradeSession session) {
        return session != null && session.getId() != null && session.getTradePlan() != null;
    }

    public TradingDirection opposite(TradingDirection d) {
        return d == TradingDirection.LONG ? TradingDirection.SHORT : TradingDirection.LONG;
    }

    public BigDecimal nvl(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x;
    }

}
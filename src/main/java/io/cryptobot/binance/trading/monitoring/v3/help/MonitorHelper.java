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

    // адаптивный трейлинг + сервис анализ волатильности
    // тестировать усреднение на линке
    //метод риск
    //1-2 дня на патч с усреднением
    //мини версия - деплой запуск пару монет - если есть фикс норм профит


    //переписать на последний по направлнию и времени открытия
    public TradeOrder getLatestActiveOrderByDirection(TradeSession session, TradingDirection dir) {
        // 1) если есть АКТИВНЫЙ усредняющий ордер по направлению — он главнее
        Optional<TradeOrder> avgActive = session.getOrders().stream()
                .filter(o -> (o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW))
                .filter(o -> dir.equals(o.getDirection()))
                .filter(o -> o.getPurpose() == OrderPurpose.AVERAGING_OPEN)
                .filter(o -> !isOpenOrderClosed(session, o))
                .max(Comparator.comparing(TradeOrder::getOrderTime));
        if (avgActive.isPresent()) return avgActive.get();

        // 2) MAIN ещё активен — возвращаем MAIN
        Optional<TradeOrder> mainOpen = session.getOrders().stream()
                .filter(o -> (o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW))
                .filter(o -> dir.equals(o.getDirection()))
                .filter(o -> o.getPurpose() == OrderPurpose.MAIN_OPEN)
                .filter(o -> !isOpenOrderClosed(session, o))
                .max(Comparator.comparing(TradeOrder::getOrderTime));

        return mainOpen.orElseGet(() -> session.getOrders().stream()
                .filter(o -> (o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW))
                .filter(o -> dir.equals(o.getDirection()))
                .filter(o -> o.getPurpose() == OrderPurpose.HEDGE_OPEN)
                .filter(o -> !isOpenOrderClosed(session, o))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null));

        // 3) MAIN закрыт — ищем последний активный HEDGE
    }

    //переписать на последний по направлнию и времени открытия
    public TradeOrder getLastFilledHedgeOrderByDirection(TradeSession session, TradingDirection dir) {
        return session.getOrders().stream()
                .filter(o -> OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .filter(o -> dir.equals(o.getDirection()))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    public boolean isOpenOrderClosed(TradeSession session, TradeOrder open) {
        if (open == null || open.getPurpose() == null) return false;

        // --- MAIN_OPEN ---
        if (OrderPurpose.MAIN_OPEN.equals(open.getPurpose())) {
            // 1) прямое закрытие MAIN
            boolean closedDirect = session.getOrders().stream()
                    .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                    .anyMatch(o -> (OrderPurpose.MAIN_CLOSE.equals(o.getPurpose()) || OrderPurpose.MAIN_PARTIAL_CLOSE.equals(o.getPurpose()))
                            && open.getOrderId().equals(o.getParentOrderId()));
            if (closedDirect) return true;

            // 2) транзитивно через усреднение: MAIN_OPEN → AVERAGING_OPEN → AVERAGING_CLOSE
            Optional<TradeOrder> lastAvgOpen = findLastAveragingOpenForParent(session, open);
            return lastAvgOpen.filter(avg -> hasAveragingCloseFor(session, avg)).isPresent();
        }

        // --- HEDGE_OPEN ---
        if (OrderPurpose.HEDGE_OPEN.equals(open.getPurpose())) {
            // 1) прямое закрытие HEDGE
            boolean closedDirect = session.getOrders().stream()
                    .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                    .anyMatch(o -> (OrderPurpose.HEDGE_CLOSE.equals(o.getPurpose()) || OrderPurpose.HEDGE_PARTIAL_CLOSE.equals(o.getPurpose()))
                            && open.getOrderId().equals(o.getParentOrderId()));
            if (closedDirect) return true;

            // 2) транзитивно через усреднение: HEDGE_OPEN → AVERAGING_OPEN → AVERAGING_CLOSE
            Optional<TradeOrder> lastAvgOpen = findLastAveragingOpenForParent(session, open);
            return lastAvgOpen.filter(avg -> hasAveragingCloseFor(session, avg)).isPresent();
        }

        // --- AVERAGING_OPEN ---
        if (OrderPurpose.AVERAGING_OPEN.equals(open.getPurpose())) {
            // 1) прямое закрытие AVERAGING_OPEN
            boolean closedDirect = session.getOrders().stream()
                    .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                    .anyMatch(o -> OrderPurpose.AVERAGING_CLOSE.equals(o.getPurpose())
                            && open.getOrderId().equals(o.getParentOrderId()));
            if (closedDirect) return true;
            
            // 2) транзитивное закрытие: если есть более позднее AVERAGING_OPEN, которое закрыто
            boolean hasLaterAveraging = session.getOrders().stream()
                    .filter(o -> OrderPurpose.AVERAGING_OPEN.equals(o.getPurpose()))
                    .filter(o -> o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW)
                    .filter(o -> o.getDirection() == open.getDirection())
                    .filter(o -> open.getOrderId().equals(o.getParentOrderId()))
                    .anyMatch(o -> isOpenOrderClosed(session, o));
            
            return hasLaterAveraging;
        }

        return false;
    }

    /**
     * MAIN считается "ещё активным" только если:
     *  - по нему нет MAIN_CLOSE/MAIN_PARTIAL_CLOSE
     *  - и НЕТ активного усреднения, которое его "перекрывает" (усреднение главнее)
     */
    public boolean isMainStillActive(TradeSession session) {
        TradeOrder main = session.getMainOrder();
        if (main == null) return false;

        // быстрые флаги по направлению
        if (main.getDirection() == TradingDirection.LONG && !session.isActiveLong()) return false;
        if (main.getDirection() == TradingDirection.SHORT && !session.isActiveShort()) return false;

        // прямое закрытие MAIN
        boolean hasMainClose = session.getOrders().stream()
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .anyMatch(o -> (OrderPurpose.MAIN_CLOSE.equals(o.getPurpose()) || OrderPurpose.MAIN_PARTIAL_CLOSE.equals(o.getPurpose()))
                        && main.getOrderId().equals(o.getParentOrderId()));
        if (hasMainClose) return false;

        // если поверх MAIN есть НЕзакрытое AVERAGING_OPEN — считаем, что MAIN «затенён» усреднением
        Optional<TradeOrder> avgOverMain = findLastAveragingOpenForParent(session, main);
        if (avgOverMain.isPresent()) {
            // Если есть усреднение, проверяем закрыто ли оно
            boolean isAveragingClosed = isOpenOrderClosed(session, avgOverMain.get());
            // MAIN активен только если усреднение закрыто
            return isAveragingClosed;
        }
        
        // Если нет усреднения, MAIN активен
        return true;
    }

    /**
     * Активность направления по ордерам:
     * приоритет AVERAGING > HEDGE > MAIN.
     */
    public boolean isDirectionActiveByOrders(TradeSession s, TradingDirection dir) {
        // 1) есть ли НЕзакрытое усреднение по направлению?
        boolean avgActive = s.getOrders().stream()
                .filter(o -> o.getPurpose() == OrderPurpose.AVERAGING_OPEN)
                .filter(o -> o.getStatus() == OrderStatus.NEW || o.getStatus() == OrderStatus.FILLED)
                .filter(o -> dir.equals(o.getDirection()))
                .anyMatch(o -> !isOpenOrderClosed(s, o));
        if (avgActive) return true;

        // 2) есть ли НЕзакрытый хедж по направлению?
        boolean hedgeActive = s.getOrders().stream()
                .filter(o -> o.getPurpose() == OrderPurpose.HEDGE_OPEN)
                .filter(o -> o.getStatus() == OrderStatus.NEW || o.getStatus() == OrderStatus.FILLED)
                .filter(o -> dir.equals(o.getDirection()))
                .anyMatch(o -> !isOpenOrderClosed(s, o));
        if (hedgeActive) return true;

        // 3) есть ли НЕзакрытый main по направлению?
        return s.getOrders().stream()
                .filter(o -> o.getPurpose() == OrderPurpose.MAIN_OPEN)
                .filter(o -> o.getStatus() == OrderStatus.NEW || o.getStatus() == OrderStatus.FILLED)
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
        if (o.getPurpose() == OrderPurpose.AVERAGING_OPEN) return OrderPurpose.AVERAGING_CLOSE;
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

    // --- AVERAGING helpers ---
    public boolean isAverageActive(TradeSession s, TradingDirection dir) {
        boolean flag = (dir == TradingDirection.LONG) ? s.isActiveAverageLong() : s.isActiveAverageShort();
        if (flag) return true;
        // проверка по факту ордеров: есть ли не закрытое AVERAGING_OPEN по направлению
        return s.getOrders().stream()
                .filter(o -> o.getPurpose() == OrderPurpose.AVERAGING_OPEN)
                .filter(o -> o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW)
                .filter(o -> dir.equals(o.getDirection()))
                .anyMatch(o -> !isOpenOrderClosed(s, o));
    }

    public boolean canOpenAverageByDirection(TradeSession session, TradingDirection dir) {
        if (dir == TradingDirection.LONG) {
            return !session.isActiveAverageLong();
        } else {
            return !session.isActiveAverageShort();
        }
    }

    public TradeOrder getLatestActiveAverageByDirection(TradeSession session, TradingDirection dir) {
        return session.getOrders().stream()
                .filter(o -> o.getPurpose() == OrderPurpose.AVERAGING_OPEN)
                .filter(o -> o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.NEW)
                .filter(o -> dir.equals(o.getDirection()))
                .filter(o -> !isOpenOrderClosed(session, o))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    public TradeOrder getLastFilledAveragingOrderByDirection(TradeSession session, TradingDirection dir) {
        return session.getOrders().stream()
                .filter(o -> OrderPurpose.AVERAGING_OPEN.equals(o.getPurpose()))
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .filter(o -> dir.equals(o.getDirection()))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    /* ----------------- helpers ----------------- */
    public Optional<TradeOrder> findLastAveragingOpenForParent(TradeSession session, TradeOrder parentOpen) {
        if (parentOpen == null) return Optional.empty();
        
        // Ищем все AVERAGING_OPEN ордера, которые ссылаются на parentOpen
        Optional<TradeOrder> directAveraging = session.getOrders().stream()
                .filter(o -> OrderPurpose.AVERAGING_OPEN.equals(o.getPurpose()))
                .filter(o -> o.getStatus() == OrderStatus.NEW || o.getStatus() == OrderStatus.FILLED)
                .filter(o -> o.getDirection() == parentOpen.getDirection())
                .filter(o -> parentOpen.getOrderId().equals(o.getParentOrderId()))
                .max(Comparator.comparing(TradeOrder::getOrderTime));
        
        if (directAveraging.isPresent()) {
            // Рекурсивно ищем усреднения для найденного усреднения
            Optional<TradeOrder> nextAveraging = findLastAveragingOpenForParent(session, directAveraging.get());
            return nextAveraging.isPresent() ? nextAveraging : directAveraging;
        }
        
        return Optional.empty();
    }

    public boolean hasAveragingCloseFor(TradeSession session, TradeOrder averagingOpen) {
        if (averagingOpen == null) return false;
        return session.getOrders().stream()
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .anyMatch(o -> OrderPurpose.AVERAGING_CLOSE.equals(o.getPurpose())
                        && averagingOpen.getOrderId().equals(o.getParentOrderId()));
    }
}
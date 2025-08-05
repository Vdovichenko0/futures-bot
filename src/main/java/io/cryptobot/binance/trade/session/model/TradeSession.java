package io.cryptobot.binance.trade.session.model;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Document(collection = "trade-session")
public class TradeSession {
    @Id
    private String id;
    private String tradePlan;

    private SessionStatus status; // ACTIVE, COMPLETED, CANCELLED

    //main order
    private Long mainPosition;
    //all orders
    private List<TradeOrder> orders = new ArrayList<>();
    // states
    private SessionMode currentMode; // SCALPING, HEDGING, POST_CLOSE, FORCING
    //when we now
    private TradingDirection direction; // LONG, SHORT
    // trailing settings
    private Boolean trailingActive = false;
    private Long trailingOrderId;

    // PnL
    private BigDecimal pnl = BigDecimal.ZERO;
    private BigDecimal totalCommission = BigDecimal.ZERO;

    // operations
    private Integer hedgeOpenCount = 0;
    private Integer hedgeCloseCount = 0;
    private Integer trailingActivations = 0;

    // context start session
    private String entryContext;

    private boolean activeLong;
    private boolean activeShort;

    // time points
    private LocalDateTime createdTime;
    @LastModifiedDate
    private LocalDateTime lastModified;
    private LocalDateTime endTime;
    private Long durationMinutes;

    public void onCreate(String plan, TradingDirection direction, TradeOrder mainOrder, String context) {
        entryContext = context;
        tradePlan = plan;
        status = SessionStatus.ACTIVE;
        currentMode = SessionMode.SCALPING;
        this.direction = direction;
        createdTime = LocalDateTime.now();
        mainPosition = mainOrder.getOrderId();
        orders.add(mainOrder);

        // Инициализация состояния позиций в зависимости от направления
        if (direction == TradingDirection.LONG) {
            openLongPosition();
        } else if (direction == TradingDirection.SHORT) {
            openShortPosition();
        }

        updateAmount();
    }

    public void addOrder(TradeOrder order) {
        if (order == null) return;
        orders.add(order);
        updatePositionState(order);
        updateAmount();
    }

    private void updatePositionState(TradeOrder order) {
        // Обновляем состояние позиций в зависимости от типа ордера
        if (order.getStatus() == OrderStatus.FILLED) {
            switch (order.getPurpose()) {
                case MAIN_OPEN:
                    // При открытии основной позиции устанавливаем соответствующее состояние
                    if (direction == TradingDirection.LONG) {
                        openLongPosition();
                    } else if (direction == TradingDirection.SHORT) {
                        openShortPosition();
                    }
                    break;
                case HEDGE_OPEN:
                    // При открытии хеджа добавляем противоположную позицию
                    if (direction == TradingDirection.LONG) {
                        openShortPosition();
                    } else if (direction == TradingDirection.SHORT) {
                        openLongPosition();
                    }
                    break;
                case HEDGE_CLOSE:
                case HEDGE_PARTIAL_CLOSE:
                    // При закрытии хеджа (полном или частичном) убираем соответствующую позицию
                    if (direction == TradingDirection.LONG) {
                        closeShortPosition();
                    } else if (direction == TradingDirection.SHORT) {
                        closeLongPosition();
                    }
                    break;
                case MAIN_CLOSE:
                case MAIN_PARTIAL_CLOSE:
                case FORCE_CLOSE:
                    // При закрытии основной позиции (полном, частичном или форсированном)
                    if (direction == TradingDirection.LONG) {
                        closeLongPosition();
                    } else if (direction == TradingDirection.SHORT) {
                        closeShortPosition();
                    }
                    break;
                case CANCEL:
                    // При отмене ордера состояние не меняется
                    break;
                default:
                    // Для других типов ордеров состояние не меняется
                    break;
            }
        }
    }

    private void updateAmount() {
        BigDecimal realizedPnl = BigDecimal.ZERO;
        BigDecimal totalComm = BigDecimal.ZERO;
        int hedgeOpens = 0;
        int hedgeCloses = 0;

        for (TradeOrder o : orders) {
            if (o.getStatus() == OrderStatus.FILLED) {
                if (o.getPnl() != null) {
                    realizedPnl = realizedPnl.add(o.getPnl());
                }
                if (o.getCommission() != null) {
                    totalComm = totalComm.add(o.getCommission());
                }

                switch (o.getPurpose()) {
                    case HEDGE_OPEN -> hedgeOpens++;
                    case HEDGE_CLOSE -> hedgeCloses++;
                    default -> {
                    }
                }
            }
        }

        this.pnl = realizedPnl;
        this.totalCommission = totalComm;
        this.hedgeOpenCount = hedgeOpens;
        this.hedgeCloseCount = hedgeCloses;
    }

    public void activateTrailing(Long idOrder) {
        if (!Boolean.TRUE.equals(trailingActive)) {
            trailingActive = true;
            trailingActivations++;
            trailingOrderId = idOrder;
        }
    }

    public void deactivateTrailing() {
        trailingActive = false;
        trailingOrderId = null;
    }

    public void changeMode(SessionMode newMode) {
        this.currentMode = newMode;
    }

    public void completeSession() {
        this.status = SessionStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        this.durationMinutes = java.time.Duration.between(createdTime, endTime).toMinutes();
        updateAmount();
    }

    // Методы для быстрого поиска ордеров
    public TradeOrder findOrderById(Long orderId) {
        return orders.stream()
                .filter(o -> orderId.equals(o.getOrderId()))
                .findFirst()
                .orElse(null);
    }

    public TradeOrder getMainOrder() {
        return findOrderById(mainPosition);
    }

    public TradeOrder getLastOrder() {
        return orders.stream()
                .max((o1, o2) -> o1.getOrderTime().compareTo(o2.getOrderTime()))
                .orElse(null);
    }

    public TradeOrder getLastHedgeOrder() {
        return orders.stream()
                .filter(o -> io.cryptobot.binance.order.enums.OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .filter(o -> io.cryptobot.binance.order.enums.OrderStatus.FILLED.equals(o.getStatus()))
                .max((o1, o2) -> o1.getOrderTime().compareTo(o2.getOrderTime()))
                .orElse(null);
    }

    // Методы для анализа состояния
    public boolean isReadyForHedgeMode() {
        return SessionMode.SCALPING.equals(currentMode)
                && pnl.compareTo(new BigDecimal("-0.002")) <= 0; // -0.2%
    }

    public boolean isReadyForTrailing() {
        return SessionMode.SCALPING.equals(currentMode)
                && pnl.compareTo(new BigDecimal("0.0017")) >= 0; // 0.17%
    }

    public boolean canCloseProfitablePosition() {
        return SessionMode.HEDGING.equals(currentMode)
                && pnl.compareTo(new BigDecimal("0.001")) >= 0; // 0.1%
    }

    public boolean needsForcingMode() {
        return pnl.compareTo(new BigDecimal("-0.005")) < 0; // -0.5%
    }

    public boolean isProfitable() {
        return pnl.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasOpenHedges() {
        return orders.stream()
                .anyMatch(o -> io.cryptobot.binance.order.enums.OrderPurpose.HEDGE_OPEN.equals(o.getPurpose())
                        && io.cryptobot.binance.order.enums.OrderStatus.FILLED.equals(o.getStatus()));
    }

    public int getOpenPositionsCount() {
        return (int) orders.stream()
                .filter(o -> io.cryptobot.binance.order.enums.OrderStatus.FILLED.equals(o.getStatus()))
                .count();
    }

    public boolean isInScalpingMode() {
        return SessionMode.SCALPING.equals(currentMode);
    }

    public boolean isInHedgeMode() {
        return SessionMode.HEDGING.equals(currentMode);
    }

    public boolean hasActiveTrailing() {
        return Boolean.TRUE.equals(trailingActive);
    }

    public String getSessionSummary() {
        return String.format("Session %s: %s %s, PnL: %s, Mode: %s, Orders: %d, Positions: %s",
                id, tradePlan, direction, pnl, currentMode, orders.size(), getPositionState());
    }

    // Методы для управления состоянием лонг/шорт позиций
    public void setLongActive(boolean active) {
        this.activeLong = active;
    }

    public void setShortActive(boolean active) {
        this.activeShort = active;
    }

    public boolean isLongActive() {
        return activeLong;
    }

    public boolean isShortActive() {
        return activeShort;
    }

    public boolean hasActivePosition() {
        return activeLong || activeShort;
    }

    public boolean hasBothPositionsActive() {
        return activeLong && activeShort;
    }

    public void closeLongPosition() {
        this.activeLong = false;
    }

    public void closeShortPosition() {
        this.activeShort = false;
    }

    public void openLongPosition() {
        this.activeLong = true;
    }

    public void openShortPosition() {
        this.activeShort = true;
    }

    // Метод для проверки возможности открытия новой позиции (для хеджирования)
    public boolean canOpenNewPosition() {
        return !hasBothPositionsActive();
    }

    // Метод для проверки, можно ли открыть хедж
    public boolean canOpenHedge() {
        return hasActivePosition() && !hasBothPositionsActive();
    }

    // Метод для получения количества активных позиций
    public int getActivePositionsCount() {
        int count = 0;
        if (activeLong) count++;
        if (activeShort) count++;
        return count;
    }

    // Метод для получения текущего состояния позиций в виде строки
    public String getPositionState() {
        if (hasBothPositionsActive()) {
            return "BOTH_ACTIVE";
        } else if (activeLong) {
            return "LONG_ACTIVE";
        } else if (activeShort) {
            return "SHORT_ACTIVE";
        } else {
            return "NO_POSITIONS";
        }
    }
}
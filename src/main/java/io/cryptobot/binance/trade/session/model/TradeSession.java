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
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Document(collection = "trade-session")
public class TradeSession {
    @Id
    @Setter
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

    // PnL
    private BigDecimal pnl = BigDecimal.ZERO;
    private BigDecimal totalCommission = BigDecimal.ZERO;
    private BigDecimal pnlTotal = BigDecimal.ZERO;

    // operations
    private Integer hedgeOpenCount = 0;
    private Integer hedgeCloseCount = 0;
    private Integer countAverageOrders = 0; // todo add +1

    // context start session
    private String entryContext;

    private boolean activeLong;
    private boolean activeShort;
    private boolean activeAverageLong; // set true when open and false when close
    private boolean activeAverageShort;
    
    // Флаг для защиты от повторной обработки
    @Setter
    private boolean processing = false;

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
        if (!hasActivePosition()) {
            completeSession();
        }
    }

    private void updatePositionState(TradeOrder order) {
        // Обновляем состояние позиций строго по фактическому направлению ордера
        if (order.getStatus() != OrderStatus.FILLED) {
            return;
        }

        switch (order.getPurpose()) {
            case MAIN_OPEN:
                if (order.getDirection() == TradingDirection.LONG) {
                    openLongPosition();
                } else if (order.getDirection() == TradingDirection.SHORT) {
                    openShortPosition();
                }
                break;

            case HEDGE_OPEN:
                if (order.getDirection() == TradingDirection.LONG) {
                    openLongPosition();
                } else if (order.getDirection() == TradingDirection.SHORT) {
                    openShortPosition();
                }
                this.currentMode = SessionMode.HEDGING;
                break;

            case HEDGE_CLOSE:
                if (order.getDirection() == TradingDirection.LONG) {
                    closeLongPosition();
                } else if (order.getDirection() == TradingDirection.SHORT) {
                    closeShortPosition();
                }
                // Если после закрытия хеджа остается только одна позиция - переходим в SCALPING
                if (!hasBothPositionsActive() && hasActivePosition()) {
                    this.currentMode = SessionMode.SCALPING;
                }
                break;
            case HEDGE_PARTIAL_CLOSE:
                // Частичное закрытие не меняет флаги позиций
                break;

            case AVERAGING_OPEN:
                if (order.getDirection() == TradingDirection.LONG) {
                    openAverageLongPosition();
                    openLongPosition();
                } else if (order.getDirection() == TradingDirection.SHORT) {
                    openAverageShortPosition();
                    openShortPosition();
                }
                break;

            case AVERAGING_CLOSE:
                if (order.getDirection() == TradingDirection.LONG) {
                    closeAverageLongPosition();
                    closeLongPosition();
                } else if (order.getDirection() == TradingDirection.SHORT) {
                    closeAverageShortPosition();
                    closeShortPosition();
                }
                break;

            case MAIN_CLOSE:
                // Если это закрытие относится к основному ордеру — очищаем ссылку на mainPosition
                if (order.getParentOrderId() != null && order.getParentOrderId().equals(mainPosition)) {
                    TradeOrder mainOrder = getMainOrder();
                    this.mainPosition = null;
                    // Закрываем позицию по направлению MAIN ордера
                    if (mainOrder != null && mainOrder.getDirection() == TradingDirection.LONG) {
                        closeLongPosition();
                    } else if (mainOrder != null && mainOrder.getDirection() == TradingDirection.SHORT) {
                        closeShortPosition();
                    }
                } else {
                    // Это закрытие хеджа - закрываем позицию по направлению ордера
                    if (order.getDirection() == TradingDirection.LONG) {
                        closeLongPosition();
                    } else if (order.getDirection() == TradingDirection.SHORT) {
                        closeShortPosition();
                    }
                }
                // Если после закрытия остается только одна позиция - переходим в SCALPING
                if (!hasBothPositionsActive() && hasActivePosition()) {
                    this.currentMode = SessionMode.SCALPING;
                }
                break;
            case MAIN_PARTIAL_CLOSE:
                // Частичное закрытие не меняет флаги позиций
                break;

            default:
                // Для других типов ордеров состояние не меняется
                break;
        }
    }

    private void updateAmount() {
        BigDecimal realizedPnl = BigDecimal.ZERO;
        BigDecimal totalComm = BigDecimal.ZERO;
        int hedgeOpens = 0;
        int hedgeCloses = 0;
        int averageOpens = 0;

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
                    case AVERAGING_OPEN -> averageOpens++;
                    default -> {
                    }
                }
            }
        }

        this.pnl = realizedPnl;
        this.totalCommission = totalComm;
        this.hedgeOpenCount = hedgeOpens;
        this.hedgeCloseCount = hedgeCloses;
        this.countAverageOrders = averageOpens;
        this.pnlTotal = pnl.subtract(totalCommission);
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
        if (orderId == null) return null;
        return orders.stream()
                .filter(o -> orderId.equals(o.getOrderId()))
                .findFirst()
                .orElse(null);
    }

    public TradeOrder getMainOrder() {
        return findOrderById(mainPosition);
    }

    public TradeOrder getLastHedgeOrder() {
        return orders.stream()
                .filter(o -> OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .max((o1, o2) -> o1.getOrderTime().compareTo(o2.getOrderTime()))
                .orElse(null);
    }

    public boolean isInScalpingMode() {
        return SessionMode.SCALPING.equals(currentMode);
    }

    public boolean isInHedgeMode() {
        return SessionMode.HEDGING.equals(currentMode);
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

    public void openAverageLongPosition() {
        this.activeAverageLong = true;
    }

    public void openAverageShortPosition() {
        this.activeAverageShort = true;
    }

    public void closeAverageLongPosition() {
        this.activeAverageLong = false;
    }

    public void closeAverageShortPosition() {
        this.activeAverageShort = false;
    }

}
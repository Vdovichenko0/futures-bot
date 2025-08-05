package io.cryptobot.binance.trade.session.model;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "orderId")
public class TradeOrder {
    private Long orderId; // binance id
    private String creationContext;
    private OrderPurpose purpose; // ENTRY, HEDGE_OPEN, HEDGE_CLOSE, TRAILING_CLOSE, FORCE_CLOSE

    // main info
    private String symbol;
    private OrderSide side; // BUY, SELL
    private String type; // MARKET, LIMIT
    private BigDecimal count;
    private BigDecimal price;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal commission = BigDecimal.ZERO; //set in usdt
    private String commissionAsset;
    private OrderStatus status; // NEW, FILLED, CANCELLED, etc
    private BigDecimal pnl = BigDecimal.ZERO;
    private int leverage; // 1-125

    // Связи с другими ордерами
    private String parentOrderId; // для хеджей - ID основного ордера
    private String relatedHedgeId; // для закрытия хеджа - ID хедж позиции

    // Режим на момент создания ордера
    private SessionMode modeAtCreation;

    // Временные метки
    private LocalDateTime orderTime; // время создания ордера
}
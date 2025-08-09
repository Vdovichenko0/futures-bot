package io.cryptobot.binance.trade.session.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "orderId")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeOrder {
    private Long orderId; // binance id
    private String creationContext;
    private OrderPurpose purpose; // ENTRY, HEDGE_OPEN, HEDGE_CLOSE, TRAILING_CLOSE, FORCE_CLOSE
    private TradingDirection direction;

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
    private Long parentOrderId; // для хеджей - ID основного ордера
    private Long relatedHedgeId; // для закрытия хеджа - ID хедж позиции

    // Режим на момент создания ордера
    private SessionMode modeAtCreation;

    // Временные метки
    private LocalDateTime orderTime; // время создания ордера

    @Setter
    private BigDecimal pnlHigh = BigDecimal.ZERO;
    @Setter
    private Boolean trailingActive = false;

    // Поля для отслеживания изменений PnL
    @Setter
    private BigDecimal basePnl; // базовая точка для отслеживания
    @Setter
    private BigDecimal maxChangePnl; // максимальное улучшение от базовой точки

    public void onCreate(Order order, BigDecimal pnl, SessionMode sessionMode, String context, TradePlan plan, TradingDirection direction, OrderPurpose purpose, Long parentOrderId, Long relatedHedgeId){
        this.orderId = order.getOrderId();
        this.direction = direction;
        this.creationContext = context;
        this.purpose = purpose;
        this.symbol = order.getSymbol();
        this.side = order.getSide();
        this.type = order.getOrderType();
        this.count = order.getQuantity();
        this.price = order.getAveragePrice();
        this.commission = order.getCommission();
        this.commissionAsset = order.getCommissionAsset();
        this.pnl = pnl;
        this.leverage = plan.getLeverage();
        this.modeAtCreation = sessionMode;
        this.orderTime = Instant.ofEpochMilli(order.getTradeTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        this.status = order.getOrderStatus();
        this.amount = order.getQuantity().multiply(order.getAveragePrice()); // количество монет * цена
        
        // Связи с другими ордерами (может быть null для первого ордера)
        this.parentOrderId = parentOrderId;
        this.relatedHedgeId = relatedHedgeId;
        
        this.trailingActive = false;
        this.pnlHigh = BigDecimal.ZERO;
        this.basePnl = null;
        this.maxChangePnl = null;
    }
}
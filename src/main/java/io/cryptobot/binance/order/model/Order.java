package io.cryptobot.binance.order.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Document(collection = "orders")
public class Order {
    @Id
    private Long orderId;                   // i

    private String symbol;                  // s
    private String clientOrderId;           // c
    private String side;                    // S (BUY/SELL)
    private String orderType;               // o (MARKET, LIMIT...)
    private String timeInForce;             // f (GTC и т.п.)
    private BigDecimal quantity;            // q
    private BigDecimal price;               // p
    private BigDecimal averagePrice;        // ap
    private BigDecimal stopPrice;           // sp
    private String executionType;           // x (TRADE, NEW...)
    private String orderStatus;             // X (FILLED, NEW...)
    private BigDecimal lastFilledQty;       // l
    private BigDecimal cumulativeFilledQty; // z
    private BigDecimal lastFilledPrice;     // L
    private BigDecimal commission;          // n
    private String commissionAsset;         // N
    private long tradeTime;                 // T
    private long tradeId;                   // t
    private boolean buyerIsMaker;           // m
    private boolean reduceOnly;             // R
    private String workingType;             // wt
    private String originalType;            // ot
    private String positionSide;            // ps
    private boolean closePosition;          // cp
    private BigDecimal realizedPnl;         // rp
    private boolean isPositionPnl;          // pP
    private int sideEffectType;             // si
    private int stopStatus;                 // ss
    private String originalResponseType;    // V
    private String positionMode;            // pm
    private long goodTillDate;              // gtd
}
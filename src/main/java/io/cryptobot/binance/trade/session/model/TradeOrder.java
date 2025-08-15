package io.cryptobot.binance.trade.session.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.enums.OrderType;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private OrderPurpose purpose; // MAIN HEDGE AVERAGE + OPEN/CLOSE
    private TradingDirection direction;

    // main info
    private String symbol;
    private OrderSide side; // BUY, SELL
    private OrderType type; // MARKET, LIMIT
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

    @Setter
    private Boolean haveAveraging = false; // if have averaging order - find it
    @Setter
    private Long idAveragingOrder;

    // Поля для отслеживания изменений PnL
    @Setter
    private BigDecimal basePnl; // базовая точка для отслеживания
    @Setter
    private BigDecimal maxChangePnl; // максимальное улучшение от базовой точки

    //create new + close order
    public void onCreate(Order order, BigDecimal pnl, SessionMode sessionMode, String context, TradePlan plan, TradingDirection direction, OrderPurpose purpose, Long parentOrderId, Long relatedHedgeId) {
        this.orderId = order.getOrderId();
        this.direction = direction;
        this.creationContext = context;
        this.purpose = purpose;
        this.symbol = order.getSymbol();
        this.side = order.getSide();
        this.type = order.getOrderType();
        this.count = order.getQuantity();

        if (order.getOrderType().equals(OrderType.LIMIT)){
            this.price = order.getPrice();
        }else{
            this.price = order.getAveragePrice();
        }

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

    //only for create new
    public void onCreateAverage(Order order, TradeOrder parentOrder, BigDecimal pnl, SessionMode sessionMode, String context, TradePlan plan, TradingDirection direction, OrderPurpose purpose) {
        // --- 0) базовые поля по фактическому fill усреднения ---
        this.orderId = order.getOrderId();
        this.direction = direction;
        this.creationContext = context;
        this.purpose = purpose;           // AVERAGING_OPEN
        this.symbol = order.getSymbol();

        // ВАЖНО: усреднение должно быть тем же направлением (BUY/SELL), что и база
        // Если parentOrder есть — унаследуем side от него (чтобы "снэпшот" был каноничным)
        this.side = order.getSide();

        this.type = order.getOrderType();
        this.status = order.getOrderStatus();
        this.leverage = plan.getLeverage();
        this.modeAtCreation = sessionMode;
        this.orderTime = Instant.ofEpochMilli(order.getTradeTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        // --- 1) связи ---
        this.parentOrderId = (parentOrder != null ? parentOrder.getOrderId() : null);
        this.relatedHedgeId = null;

        // --- 2) приведём к безопасным BigDecimal ---
        BigDecimal baseQty   = (parentOrder != null && parentOrder.getCount() != null) ? parentOrder.getCount() : BigDecimal.ZERO;
        BigDecimal basePrice = (parentOrder != null && parentOrder.getPrice() != null) ? parentOrder.getPrice() : BigDecimal.ZERO;
        BigDecimal baseComm  = (parentOrder != null && parentOrder.getCommission() != null) ? parentOrder.getCommission() : BigDecimal.ZERO;

        BigDecimal addQty    = (order.getQuantity() != null) ? order.getQuantity() : BigDecimal.ZERO;

        BigDecimal priceLM;
        if (order.getOrderType().equals(OrderType.LIMIT)){
            priceLM = order.getPrice();
        }else{
            priceLM = order.getAveragePrice();
        }

        BigDecimal addPrice  = (priceLM != null) ? priceLM : BigDecimal.ZERO;
        BigDecimal addComm   = (order.getCommission() != null) ? order.getCommission() : BigDecimal.ZERO;

        // --- 3) объединённые величины ---
        BigDecimal totalQty = baseQty.add(addQty);
        // средневзвешенная цена = (baseQty*basePrice + addQty*addPrice) / totalQty
        BigDecimal avgPrice;
        if (totalQty.signum() > 0) {
            avgPrice = baseQty.multiply(basePrice)
                    .add(addQty.multiply(addPrice))
                    .divide(totalQty, 8, RoundingMode.HALF_UP);
        } else {
            // fallback на случай аномалии
            avgPrice = addPrice;
        }

        // Итоговая комиссия (в USDT, по твоему комменту)
        BigDecimal totalCommission = baseComm.add(addComm);

        // Итоговая "стоимость" позиции в терминах снапшота усреднения
        BigDecimal totalAmount = totalQty.multiply(avgPrice);

        // --- 4) записываем в текущий (усредняющий) ордер уже СЛИТЫЕ значения ---
        this.count = totalQty;            // <== ОБЩЕЕ количество (старый + новый)
        this.price = avgPrice;            // <== НОВАЯ средняя цена
        this.amount = totalAmount;        // <== totalQty * avgPrice

        // Комиссию суммируем (в USDT), чтобы "снэпшот" отражал полную себестоимость
        this.commission = totalCommission;
        // Если ты везде хранишь комиссию уже в USDT — зафиксируем это явно
        this.commissionAsset = "USDT";

        // pnl сейчас 0 (мы только открыли усреднение), но поддержим входной параметр на будущее
        this.pnl = (pnl == null ? BigDecimal.ZERO : pnl);

        // --- 5) trailing/PnL-трекинг — обнуляем ---
        this.trailingActive = false;
        this.pnlHigh = BigDecimal.ZERO;
        this.basePnl = null;
        this.maxChangePnl = null;

        // --- 6) метки на базовом ордере (для UI/объединённого закрытия и мониторинга) ---
        if (parentOrder != null) {
            parentOrder.setHaveAveraging(true);
            parentOrder.setIdAveragingOrder(this.orderId);
        }
    }

}
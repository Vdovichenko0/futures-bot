package io.cryptobot.binance.trading.process;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.model.Order;
import io.cryptobot.binance.order.service.OrderService;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingProcessServiceImpl implements TradingProcessService{
    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;
    private final OrderService orderService;

    @Override
    @Transactional
    public void openOrder(TradePlan plan, TradingDirection direction, BigDecimal currentPrice, String context) {
        //calc count - amount/current price / lot-tick size
        String coin = plan.getSymbol();
        BigDecimal amount = plan.getAmountPerTrade();
        BigDecimal lotSize = plan.getSizes().getLotSize();
        //calc
        BigDecimal count = amount.divide(currentPrice, 8, RoundingMode.DOWN);

        if (count.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid quantity for " + coin + count);
        }
        count = count.divide(lotSize, 0, RoundingMode.DOWN).multiply(lotSize);

        //create order
        Order orderOpen = orderService.createOrder(coin, count.doubleValue(), OrderSide.BUY, true);
        //check filled
        boolean filled = waitForFilledOrder(orderOpen, 5000, 500);
        if (!filled) {
            log.warn("Order {} was not filled in time", orderOpen.getOrderId());
            return;
        }

        //build trade order
        TradeOrder mainOrder = buildTradeOrder(orderOpen, context, plan, direction);
        //create session
        TradeSession session = sessionService.create(coin, direction, mainOrder, context);
        //send to monitoring
        //need to realize
    }

    //open order + create session
    // get request to close session - sell orders

    //==========================
    //plan:
    //create order
    //save order id
    //check filled
    //create session
    //send to monitoring service

    private boolean waitForFilledOrder(Order order, int maxWaitMillis, int intervalMillis) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Order updated = orderService.getOrder(order.getOrderId());
            if (updated.getOrderStatus().equals(OrderStatus.FILLED)) {
                log.info("✅ Order {} is filled", order.getOrderId());
                return true;
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("⚠️ Order {} not filled after {}ms", order.getOrderId(), maxWaitMillis);
        return false;
    }


    private TradeOrder buildTradeOrder(Order order, String context, TradePlan plan, TradingDirection direction){
        return TradeOrder.builder()
                .orderId(order.getOrderId())
                .direction(direction)
                .creationContext(context)
                .purpose(OrderPurpose.MAIN_OPEN)
                .symbol(order.getSymbol())
                .side(order.getSide())
                .type("MARKET")
                .count(order.getQuantity())
                .price(order.getAveragePrice())
                .commission(order.getCommission())
                .commissionAsset(order.getCommissionAsset()) //todo to usdt commission if need
                .pnl(BigDecimal.ZERO)
                .leverage(plan.getLeverage())
                .modeAtCreation(SessionMode.SCALPING)
                .orderTime(
                        Instant.ofEpochMilli(order.getTradeTime())
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                )
                .build();
    }
}
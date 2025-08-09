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
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.binance.trading.monitoring.v2.MonitoringServiceV2;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingProcessServiceImpl implements TradingProcessService{
    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;
    private final OrderService orderService;
    private final MonitoringServiceV2 monitoringService;
    private final TradePlanGetService tradePlanGetService;
    @Getter
    @Setter
    private int maxWaitMillis = 15000;
    @Getter
    @Setter
    private int intervalMillis = 200;

    @Scheduled(initialDelay = 10_000)
    public void init(){
        TradePlan plan = tradePlanGetService.getPlan("LINKUSDC");
        openOrder(plan, TradingDirection.SHORT, BigDecimal.valueOf(21.414), "test");
    }

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
        log.info("count {}", count);
        //create order
        OrderSide side = direction.equals(TradingDirection.SHORT) ? OrderSide.SELL : OrderSide.BUY;
        Order orderOpen = orderService.createOrder(coin, count.doubleValue(), side, true);
        
        //check if order was created
        if (orderOpen == null) {
            log.warn("Order creation failed for {}", coin);
            return;
        }
        
        //check filled
        boolean filled = waitForFilledOrder(orderOpen, maxWaitMillis, intervalMillis);
        if (!filled) {
            log.warn("Order {} was not filled in time", orderOpen.getOrderId());
            return;
        }
        Order filledOrder = orderService.getOrder(orderOpen.getOrderId());

        //build trade order
        TradeOrder mainOrder = new TradeOrder();
        mainOrder.onCreate(filledOrder, BigDecimal.ZERO, SessionMode.SCALPING, context, plan, direction, OrderPurpose.MAIN_OPEN, null, null);
        //create session
        TradeSession session = sessionService.create(coin, direction, mainOrder, context);
        //send to monitoring
        //need to realize
        monitoringService.addToMonitoring(session);
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

    public boolean waitForFilledOrder(Order order, int maxWaitMillis, int intervalMillis) {
        if (order == null) {
            return false;
        }
        
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Order updated = orderService.getOrder(order.getOrderId());
            if (updated != null && updated.getOrderStatus().equals(OrderStatus.FILLED)) {
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
}
package io.cryptobot.binance.trading.monitoring.v3.utils;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckAveraging {
    private final MonitorHelper monitorHelper;
    private static final BigDecimal POSITION_DOWN = new BigDecimal("3"); //3% my pnl, not roi

    /*
    AVERAGING_OPEN
    AVERAGING_CLOSE
     */

    //check position
    //check if already opened for this direction average order
    //if yes - return
    //no - open
    //only one active average order for one direction
    //one long one short, when close average order reset

    //get here lowest position, only in mode two positions

    public boolean checkOpen(TradeSession session,TradeOrder order, BigDecimal currentPnl){

        //check pnl
        // < POSITION_DOWN - false
        // > POSITION_DOWN ->
        // check open average by direction
        // yes - false
        // true - true
        if (order == null || currentPnl == null) return false;

        // Базовый ордер должен быть активным (FILLED)
        if (!OrderStatus.FILLED.equals(order.getStatus())) {
            return false;
        }

        // Сам ордер не должен быть усредняющим
        if (OrderPurpose.AVERAGING_OPEN.equals(order.getPurpose())) {
            return false;
        }

        if (!monitorHelper.canOpenAverageByDirection(session, order.getDirection())) {
            return false;
        }

        BigDecimal threshold = POSITION_DOWN.negate(); // -3%
        if (currentPnl.compareTo(threshold) <= 0) {
            return true;
        }

        return false;
    }
}

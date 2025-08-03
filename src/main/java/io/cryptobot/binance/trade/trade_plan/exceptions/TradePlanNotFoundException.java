package io.cryptobot.binance.trade.trade_plan.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TradePlanNotFoundException extends RuntimeException {
    public TradePlanNotFoundException() {
        super("Trade plan not found.");
    }
}
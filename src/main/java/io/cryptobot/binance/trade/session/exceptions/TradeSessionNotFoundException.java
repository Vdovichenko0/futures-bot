package io.cryptobot.binance.trade.session.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TradeSessionNotFoundException extends RuntimeException {
    public TradeSessionNotFoundException() {
        super("Trade session not found.");
    }
}

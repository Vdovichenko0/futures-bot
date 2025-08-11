package io.cryptobot.binance.trade.session.service.handle_work;

import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.TradingDirection;

public interface SessionHandleActionsService {
    SessionDto closeAllActiveOrders(String idSession);

    SessionDto closeSession(String idSession);

    SessionDto closeOrderByDirection(String idSession, TradingDirection direction);
}
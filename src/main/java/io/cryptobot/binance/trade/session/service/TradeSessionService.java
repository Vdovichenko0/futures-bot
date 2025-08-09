package io.cryptobot.binance.trade.session.service;

import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;

import java.util.List;

public interface TradeSessionService {
    TradeSession create(String plan, TradingDirection direction, TradeOrder mainOrder, String context);

    TradeSession getById(String idPlan);

    List<TradeSession> getAllByPlan(String plan);

    List<TradeSession> getAllActive();

    List<TradeSession> getAll();

    TradeSession addOrder(String idSession, TradeOrder order);

    TradeSession closeSession(String idSession);

}
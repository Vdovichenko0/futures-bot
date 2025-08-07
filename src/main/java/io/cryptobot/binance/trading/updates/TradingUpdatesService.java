package io.cryptobot.binance.trading.updates;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeSession;

import java.math.BigDecimal;

public interface TradingUpdatesService {
    TradeSession closePosition(TradeSession session, SessionMode sessionMode, Long idOrder, Long relatedHedgeId, TradingDirection direction, OrderPurpose purpose, BigDecimal currentPrice, String context);

    TradeSession openPosition(TradeSession session, SessionMode sessionMode, TradingDirection direction, OrderPurpose purpose, BigDecimal currentPrice, String context, Long parentOrderId, Long relatedHedgeId);

    }

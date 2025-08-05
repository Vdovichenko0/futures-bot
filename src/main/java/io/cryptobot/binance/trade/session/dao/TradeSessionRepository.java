package io.cryptobot.binance.trade.session.dao;

import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.model.TradeSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TradeSessionRepository extends MongoRepository<TradeSession, String> {
    List<TradeSession> findAllByStatus(SessionStatus status);

    List<TradeSession> findAllByTradePlan(String tradePlan);
}

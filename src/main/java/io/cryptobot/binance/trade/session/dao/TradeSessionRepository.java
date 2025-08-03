package io.cryptobot.binance.trade.session.dao;

import io.cryptobot.binance.trade.session.model.TradeSession;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TradeSessionRepository extends MongoRepository<TradeSession, String> {
}

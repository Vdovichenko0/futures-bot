package io.cryptobot.binance.trade.trade_plan.dao;

import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TradePlanRepository extends MongoRepository<TradePlan, String> {
}

package io.cryptobot.binance.trade.trade_plan.service.get;

import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.exceptions.TradePlanNotFoundException;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanGetServiceImpl implements TradePlanGetService{
    private final TradePlanRepository repository;

    @Override
    @Transactional
    @Cacheable(value = "tradePlanGetPlan", key = "#symbol")
    public TradePlan getPlan(String symbol) {
        log.debug("Cache miss for getPlan: {}", symbol);
        return repository.findById(symbol).orElseThrow(TradePlanNotFoundException::new);
    }

    @Override
    @Transactional
    @Cacheable(value = "tradePlanGetAll")
    public List<TradePlan> getAll() {
        log.debug("Cache miss for getAll");
        return repository.findAll();
    }

    @Override
    @Transactional
    @Cacheable(value = "tradePlanGetAllActiveTrue")
    public List<TradePlan> getAllActiveTrue() {
        log.debug("Cache miss for getAllActiveTrue");
        return repository.findAllByActiveIsTrue();
    }

    @Override
    @Transactional
    @Cacheable(value = "tradePlanGetAllActiveFalse")
    public List<TradePlan> getAllActiveFalse() {
        log.debug("Cache miss for getAllActiveFalse");
        return repository.findAllByActiveIsFalse();
    }
}

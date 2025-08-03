package io.cryptobot.binance.trade.trade_plan.service.get;

import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.exceptions.TradePlanNotFoundException;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public TradePlan getPlan(String symbol) {
        return repository.findById(symbol).orElseThrow(TradePlanNotFoundException::new);
    }

    @Override
    @Transactional
    public List<TradePlan> getAll() {
        return repository.findAll();
    }
}

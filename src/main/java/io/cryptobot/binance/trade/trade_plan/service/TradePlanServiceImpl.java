package io.cryptobot.binance.trade.trade_plan.service;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.helper.TradePlanHelper;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.configs.locks.TradePlanLockRegistry;
import io.cryptobot.helpers.SymbolHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanServiceImpl implements TradePlanService{
    private final ModelMapper modelMapper;
    private final TradePlanLockRegistry lockRegistry;
    private final TradePlanRepository repository;
    private final BinanceService binanceService;

    @Override
    @Transactional
    public TradePlan createPlan(TradePlanCreateDto dto) {
        TradePlanHelper.validateCreatePlan(dto);
        //set leverage +
        //set margin mode +
        //check unique +

        if (repository.existsById(dto.getSymbol())) throw new IllegalArgumentException("Plan already exists.");
        binanceService.setLeverage(dto.getSymbol(),dto.getLeverage());
        binanceService.setMarginType(dto.getSymbol(), false); //params.put("marginType", isolated ? "ISOLATED" : "CROSSED");
        SizeModel sizeModel = SymbolHelper.getSizeModel(dto.getSymbol());
        TradePlan plan = new TradePlan();
        plan.onCreate(dto, sizeModel);
        log.info(plan.toString());
        //todo check + save
        return repository.save(plan);
    }
}

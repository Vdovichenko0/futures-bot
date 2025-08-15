package io.cryptobot.binance.trade.trade_plan.service;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.exceptions.TradePlanNotFoundException;
import io.cryptobot.binance.trade.trade_plan.helper.TradePlanHelper;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.cache.TradePlanCacheManager;
import io.cryptobot.binance.trading.process.TradingProcessService;
import io.cryptobot.configs.locks.TradePlanLockRegistry;
import io.cryptobot.helpers.SymbolHelper;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import io.cryptobot.utils.MarketDataSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanServiceImpl implements TradePlanService {
    private final ModelMapper modelMapper;
    private final TradePlanLockRegistry lockRegistry;
    private final TradePlanRepository repository;
    private final BinanceService binanceService;
    private final TradePlanCacheManager cacheManager;
    private final MarketDataSubscriptionService dataSubscriptionService;
    //todo remove
    private final TradingProcessService tradingProcessService;
    private final Ticker24hService ticker24hService;

    @Override
    @Transactional
    public TradePlan createPlan(TradePlanCreateDto dto) {
        TradePlanHelper.validateCreatePlan(dto);
        //set leverage +
        //set margin mode +
        //check unique +

        if (repository.existsById(dto.getSymbol())) throw new IllegalArgumentException("Plan already exists.");
        binanceService.setLeverage(dto.getSymbol(), dto.getLeverage());
        binanceService.setMarginType(dto.getSymbol(), false); //params.put("marginType", isolated ? "ISOLATED" : "CROSSED");

        Map<String, SizeModel> sizeModelMap = SymbolHelper.getSizeModels(List.of(dto.getSymbol())); //todo

        SizeModel sizeModel = sizeModelMap.get(dto.getSymbol());
        TradeMetrics metrics = modelMapper.map(dto.getMetrics(), TradeMetrics.class);
        TradePlan plan = new TradePlan();
        plan.onCreate(dto.getSymbol(), dto.getAmountPerTrade(), dto.getLeverage(), metrics, sizeModel);
        log.info(plan.toString());
        //todo check + save
        // update websocket etc. all cycle of klines/depth/aggTrade/ticker24h
        TradePlan savedPlan = repository.save(plan);

        cacheManager.evictListCaches();
        dataSubscriptionService.subscribe(dto.getSymbol());
        return savedPlan;
    }

    @Override
    @Transactional
    public List<String> createManyPlans(List<TradePlanCreateDto> dtos) {
        List<TradePlan> created = new ArrayList<>();
        for (TradePlanCreateDto dto : dtos) {
            try {
                TradePlan newPlan = createPlan(dto);
                created.add(newPlan);
            } catch (Exception e) {
                log.error("Error creating {}", dto.getSymbol());
            }
        }
        return created.stream()
                .map(TradePlan::getSymbol)
                .toList();
    }

    @Override
    @Transactional
    public void startSession(String coin, String context, TradingDirection direction) {
        TradePlan plan = repository.findById(coin).orElseThrow(TradePlanNotFoundException::new);
        BigDecimal price = ticker24hService.getPrice(coin);
        if (plan.getActive()) {
            throw new IllegalArgumentException("Plan already working."); //todo new
        }
        tradingProcessService.openOrder(plan, direction, price, context);
    }
}

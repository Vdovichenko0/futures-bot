package io.cryptobot.binance.trade.trade_plan.service.update;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.binance.model.LeverageMarginInfo;
import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.dto.TradeMetricsDto;
import io.cryptobot.binance.trade.trade_plan.helper.TradePlanHelper;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.cache.TradePlanCacheManager;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.configs.locks.TradePlanLockRegistry;
import io.cryptobot.helpers.SymbolHelper;
import io.cryptobot.utils.LockType;
import io.cryptobot.utils.lock.single_lock.WithLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanUpdateServiceImpl implements TradePlanUpdateService {
    private final TradePlanGetService tradePlanGetService;
    private final TradePlanRepository repository;
    private final BinanceService binanceService;
    private final TradePlanLockRegistry lockRegistry;
    private final TradePlanCacheManager cacheManager;

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public TradePlan updateLeverage(String idPlan, int leverage) {
        TradePlanHelper.validateLeverage(leverage);
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.putLeverage(leverage);
        binanceService.setLeverage(idPlan, leverage);
        repository.save(plan);
        cacheManager.evictPlanAndListCaches(idPlan);
        return plan;
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public TradePlan updateAmount(String idPlan, BigDecimal amount) {
        TradePlanHelper.validateAmountPerTrade(amount);
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.updateAmount(amount);
        repository.save(plan);
        cacheManager.evictPlanAndListCaches(idPlan);
        return plan;
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public TradePlan updateMetrics(String idPlan, TradeMetricsDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Request DTO cannot be null");
        }

        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        TradeMetrics metricsActual = plan.getMetrics();
        boolean updated = false;
        if (dto.getMinLongPct()!=null){
            TradePlanHelper.validatePercentage(dto.getMinLongPct(), "minLongPct");
            metricsActual.setMinLongPct(dto.getMinLongPct());
            updated = true;
        }
        if (dto.getMinShortPct()!=null){
            TradePlanHelper.validatePercentage(dto.getMinShortPct(), "minShortPct");
            metricsActual.setMinShortPct(dto.getMinShortPct());
            updated = true;
        }
        if (dto.getMinImbalanceLong()!=null){
            TradePlanHelper.validateRatioRange(dto.getMinImbalanceLong(), 0.0, 1.0, "minImbalanceLong");
            metricsActual.setMinImbalanceLong(dto.getMinImbalanceLong());
            updated = true;
        }
        if (dto.getMaxImbalanceShort()!=null){
            TradePlanHelper.validateRatioRange(dto.getMaxImbalanceShort(), 0.0, 1.0, "maxImbalanceShort");
            metricsActual.setMaxImbalanceShort(dto.getMaxImbalanceShort());
            updated = true;
        }
        if (dto.getEmaSensitivity()!=null){
            TradePlanHelper.validatePositiveDouble(dto.getEmaSensitivity(), "emaSensitivity", 0.0, 1.0);
            metricsActual.setEmaSensitivity(dto.getEmaSensitivity());
            updated = true;
        }
        if (dto.getVolRatioThreshold()!=null){
            TradePlanHelper.validatePositiveDouble(dto.getVolRatioThreshold(), "volRatioThreshold", 0.0, 100.0);
            metricsActual.setVolRatioThreshold(dto.getVolRatioThreshold());
            updated = true;
        }
        if (dto.getVolWindowSec() > 0) {
            TradePlanHelper.validatePositiveInt(dto.getVolWindowSec(), "volWindowSec", 1, 600);
            metricsActual.setVolWindowSec(dto.getVolWindowSec());
            updated = true;
        }
        if (dto.getDepthLevels() > 0) {
            TradePlanHelper.validatePositiveInt(dto.getDepthLevels(), "depthLevels", 1, 500);
            metricsActual.setDepthLevels(dto.getDepthLevels());
            updated = true;
        }

        if (updated) repository.save(plan);
        return plan;
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void addProfit(String idPlan, BigDecimal profit) {
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.addProfit(profit);
        repository.save(plan);
        cacheManager.evictPlanAndListCaches(idPlan);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void openPlan(String idPlan) {
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.openPlan();
        repository.save(plan);
        cacheManager.evictPlanAndListCaches(idPlan);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void closePlan(String idPlan) {
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        if (plan.getActive()) {
            throw new IllegalArgumentException("Cant close plan when he active, need wait when session be closed");
        }
        plan.closePlan();
        repository.save(plan);
        cacheManager.evictPlanAndListCaches(idPlan);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void setActiveTrue(String idPlan, String idNewSession) {
        if (idNewSession == null || idNewSession.isBlank())
            throw new IllegalArgumentException("session id cant be null or blank");
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.closeActive(idNewSession);
        repository.save(plan);
        cacheManager.evictPlanAndListCaches(idPlan);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void setActiveTrueFalse(String idPlan) {
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.openActive();
        repository.save(plan);
        cacheManager.evictPlanAndListCaches(idPlan);
    }

    @Override
    @Transactional
    @Scheduled(initialDelay = 10_000, fixedRate = 2, timeUnit = TimeUnit.HOURS)
    public void scheduledUpdateSizes() {
        List<TradePlan> tradePlans = repository.findAll();
        if (tradePlans.isEmpty()) {
            return;
        }
        log.info("count trades to update size {}", tradePlans.size());
        tradePlans.sort(Comparator.comparing(TradePlan::getSymbol));

        List<ReentrantLock> locks = new ArrayList<>(tradePlans.size());
        for (TradePlan plan : tradePlans) {
            ReentrantLock lock = lockRegistry.getLock(plan.getSymbol());
            lock.lock();
            locks.add(lock);
        }

        try {
            Map<String, SizeModel> sizeModelMap = SymbolHelper.getSizeModels(
                    tradePlans.stream()
                            .map(TradePlan::getSymbol)
                            .toList()
            );

            List<TradePlan> toSave = new ArrayList<>();
            for (TradePlan plan : tradePlans) {
                SizeModel newSize = sizeModelMap.get(plan.getSymbol());
                if (newSize != null) {
                    plan.updateSizes(newSize);
                    toSave.add(plan);
                }
            }

            if (!toSave.isEmpty()) {
                log.info("updates trades {}", toSave.size());
                repository.saveAll(toSave);
                cacheManager.evictAllTradePlanCaches();
            }
        } finally {
            locks.forEach(ReentrantLock::unlock);
        }
    }

    @Override
    @Transactional
    @Scheduled(initialDelay = 10_000, fixedRate = 6, timeUnit = TimeUnit.HOURS)
    public void scheduledSendRequestUpdateLeverage() {
        List<TradePlan> tradePlans = repository.findAll();
        if (tradePlans.isEmpty()) {
            return;
        }
        for (TradePlan plan: tradePlans){
            LeverageMarginInfo leverageMarginInfo = binanceService.getLeverageAndMarginMode(plan.getSymbol());
            if (leverageMarginInfo.getLeverage() != plan.getLeverage()){
                binanceService.setLeverage(plan.getSymbol(), plan.getLeverage());
            }
            if (leverageMarginInfo.isIsolated()){
                binanceService.setMarginType(plan.getSymbol(), false);
            }

        }
    }

}
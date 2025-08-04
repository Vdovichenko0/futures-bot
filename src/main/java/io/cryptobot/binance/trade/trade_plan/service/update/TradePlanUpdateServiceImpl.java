package io.cryptobot.binance.trade.trade_plan.service.update;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.binance.model.LeverageMarginInfo;
import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.helper.TradePlanHelper;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
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
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanUpdateServiceImpl implements TradePlanUpdateService {
    private final TradePlanGetService tradePlanGetService;
    private final TradePlanRepository repository;
    private final BinanceService binanceService;
    private final TradePlanLockRegistry lockRegistry;

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public TradePlan updateLeverage(String idPlan, int leverage) {
        TradePlanHelper.validateLeverage(leverage);
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.putLeverage(leverage);
        binanceService.setLeverage(idPlan, leverage);
        repository.save(plan);
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
        return plan;
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void addProfit(String idPlan, BigDecimal profit) {
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.addProfit(profit);
        repository.save(plan);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void openPlan(String idPlan) {
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.openPlan();
        repository.save(plan);
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
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public void setActiveTrueFalse(String idPlan) {
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.openActive();
        repository.save(plan);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public TradePlan updateImbalance(String idPlan, BigDecimal imb) {
        TradePlanHelper.validateMetricValue(imb, "imbalance", -100, 100);
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.getMetrics().updateImbalance(imb);
        repository.save(plan);
        return plan;
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.PLAN, keyParam = "idPlan")
    public TradePlan updateRatio(String idPlan, BigDecimal ratio) {
        TradePlanHelper.validateMetricValue(ratio, "ratio", -100, 100);
        TradePlan plan = tradePlanGetService.getPlan(idPlan);
        plan.getMetrics().updateRatio(ratio);
        repository.save(plan);
        return plan;
    }

    @Override
    @Transactional
    @Scheduled(fixedRate = 600_000)
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
            }
        } finally {
            locks.forEach(ReentrantLock::unlock);
        }
    }

    @Override
    @Transactional
    @Scheduled(fixedRate = 700_000)
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

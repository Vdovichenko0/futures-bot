package io.cryptobot.helpers;

import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MainHelper {
    private final TradePlanGetService tradePlanGetService;

    public List<String> getSymbolsFromPlans(){
        return tradePlanGetService.getAll().
                stream()
                .map(TradePlan::getSymbol)
                .toList();
    }

}

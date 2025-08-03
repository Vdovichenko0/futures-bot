package io.cryptobot.binance.trade.trade_plan.controller;

import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.TradePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/trade-plan")
public class TradePlanController {
    private final TradePlanService tradePlanService;
//    private final TradePlanUpdateService tradePlanUpdateService;
//    private final TradePlanGetService tradePlanGetService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public TradePlan create(@RequestBody TradePlanCreateDto dto){
        return tradePlanService.createPlan(dto);
    }
}

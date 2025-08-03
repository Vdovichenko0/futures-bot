package io.cryptobot.binance.trade.trade_plan.controller;

import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.TradePlanService;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/trade-plan")
public class TradePlanController {
    private final TradePlanService tradePlanService;
    //    private final TradePlanUpdateService tradePlanUpdateService;
    private final TradePlanGetService tradePlanGetService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public TradePlan create(@RequestBody TradePlanCreateDto dto) {
        return tradePlanService.createPlan(dto);
    }

    @GetMapping("/{symbol}")
    @ResponseStatus(HttpStatus.OK)
    public TradePlan getPlan(@PathVariable String symbol) {
        return tradePlanGetService.getPlan(symbol);
    }

    @GetMapping("/all")
    @ResponseStatus(HttpStatus.OK)
    public List<TradePlan> getAll() {
        return tradePlanGetService.getAll();
    }
}

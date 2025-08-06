package io.cryptobot.binance.trade.trade_plan.controller;

import io.cryptobot.binance.trade.trade_plan.dto.TradeMetricsDto;
import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.TradePlanService;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.binance.trade.trade_plan.service.update.TradePlanUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/trade-plan")
public class TradePlanController {
    private final TradePlanService tradePlanService;
    private final TradePlanUpdateService tradePlanUpdateService;
    private final TradePlanGetService tradePlanGetService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public TradePlan create(@RequestBody TradePlanCreateDto dto) {
        return tradePlanService.createPlan(dto);
    }

    @PostMapping("/create/many")
    @ResponseStatus(HttpStatus.CREATED)
    public List<String> createManyPlans(@RequestBody List<TradePlanCreateDto> dtos){
        return tradePlanService.createManyPlans(dtos);
    }

    @PutMapping("/{idPlan}/update/metrics")
    @ResponseStatus(HttpStatus.OK)
    public TradePlan updateMetrics(@PathVariable String idPlan,@RequestBody TradeMetricsDto dto){
        return tradePlanUpdateService.updateMetrics(idPlan, dto);
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

    @PutMapping("/{idPlan}/leverage")
    @ResponseStatus(HttpStatus.OK)
    public TradePlan updateLeverage(@PathVariable String idPlan, @RequestParam int leverage) {
        return tradePlanUpdateService.updateLeverage(idPlan, leverage);
    }

    @PutMapping("/{idPlan}/amount")
    @ResponseStatus(HttpStatus.OK)
    public TradePlan updateAmount(@PathVariable String idPlan, @RequestParam BigDecimal amount) {
        return tradePlanUpdateService.updateAmount(idPlan, amount);
    }

    @PutMapping("/{idPlan}/open")
    @ResponseStatus(HttpStatus.OK)
    public void openPlan(@PathVariable String idPlan) {
        tradePlanUpdateService.openPlan(idPlan);
    }

    @PutMapping("/{idPlan}/close")
    @ResponseStatus(HttpStatus.OK)
    public void closePlan(@PathVariable String idPlan) {
        tradePlanUpdateService.closePlan(idPlan);
    }
}

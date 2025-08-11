package io.cryptobot.binance.trade.session.controller;

import io.cryptobot.binance.trade.session.dto.PnlResultDto;
import io.cryptobot.binance.trade.session.dto.SessionAllDto;
import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.service.get.TradeSessionGetService;
import io.cryptobot.binance.trade.session.service.handle_work.SessionHandleActionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/session")
public class SessionController {
    private final TradeSessionGetService sessionGetService;
    private final SessionHandleActionsService handleActionsService;

    @GetMapping("/all")
    @ResponseStatus(HttpStatus.OK)
    public List<SessionAllDto> getAll() {
        return sessionGetService.getAll();
    }

    @GetMapping("/{idSession}")
    @ResponseStatus(HttpStatus.OK)
    public SessionDto getById(@PathVariable String idSession) {
        return sessionGetService.getById(idSession);
    }

    @GetMapping("/plan/{plan}")
    @ResponseStatus(HttpStatus.OK)
    public List<SessionAllDto> getAllByPlan(@PathVariable String plan) {
        return sessionGetService.getAllByPlan(plan);
    }

    @GetMapping("/status/{status}")
    @ResponseStatus(HttpStatus.OK)
    public List<SessionAllDto> getAllByStatus(@PathVariable SessionStatus status) {
        return sessionGetService.getAllByStatus(status);
    }

    @GetMapping("/pnl/all")
    @ResponseStatus(HttpStatus.OK)
    public PnlResultDto calcPnlAll() {
        return sessionGetService.calcPnlAll();
    }

    @GetMapping("/pnl/plan/{plan}")
    @ResponseStatus(HttpStatus.OK)
    public PnlResultDto calcPnlByPlan(@PathVariable String plan) {
        return sessionGetService.calcPnlByPlan(plan);
    }

    @GetMapping("/{idSession}/orders")
    @ResponseStatus(HttpStatus.OK)
    public List<TradeOrder> getOrders(@PathVariable String idSession) {
        return sessionGetService.getOrders(idSession);
    }

    @GetMapping("/pnl/time-range")
    @ResponseStatus(HttpStatus.OK)
    public PnlResultDto calcPnlByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return sessionGetService.calcPnlByTimeRange(from, to);
    }

    @GetMapping("/pnl/time-range/symbol/{symbol}")
    @ResponseStatus(HttpStatus.OK)
    public PnlResultDto calcPnlByTimeRangeAndSymbol(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return sessionGetService.calcPnlByTimeRangeAndSymbol(from, to, symbol);
    }

    //handle actions
    @PutMapping("/{idSession}/close-all")
    @ResponseStatus(HttpStatus.OK)
    public SessionDto closeAllActiveOrders(@PathVariable String idSession){
        return handleActionsService.closeAllActiveOrders(idSession);
    }

    @PutMapping("/{idSession}/close")
    @ResponseStatus(HttpStatus.OK)
    public SessionDto closeSession(@PathVariable String idSession){
        return handleActionsService.closeSession(idSession);
    }

    @PutMapping("/{idSession}/{direction}/close-order")
    @ResponseStatus(HttpStatus.OK)
    public SessionDto closeOrderByDirection(@PathVariable String idSession,@PathVariable TradingDirection direction){
        return handleActionsService.closeOrderByDirection(idSession, direction);
    }
}
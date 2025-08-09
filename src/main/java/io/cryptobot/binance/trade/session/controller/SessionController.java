package io.cryptobot.binance.trade.session.controller;

import io.cryptobot.binance.trade.session.dto.PnlResultDto;
import io.cryptobot.binance.trade.session.dto.SessionAllDto;
import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.service.get.TradeSessionGetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/session")
public class SessionController {
    private final TradeSessionGetService sessionGetService;

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
}

package io.cryptobot.binance.trade.session.service.get;

import io.cryptobot.binance.trade.session.dto.PnlResultDto;
import io.cryptobot.binance.trade.session.dto.SessionAllDto;
import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.SessionStatus;

import java.util.List;

public interface TradeSessionGetService {
    List<SessionAllDto> getAll();

    SessionDto getById(String idSession);

    List<SessionAllDto> getAllByPlan(String plan);

    List<SessionAllDto> getAllByStatus(SessionStatus status);

    PnlResultDto calcPnlAll();

    PnlResultDto calcPnlByPlan(String plan);
}
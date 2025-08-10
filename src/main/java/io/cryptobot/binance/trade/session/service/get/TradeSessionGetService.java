package io.cryptobot.binance.trade.session.service.get;

import io.cryptobot.binance.trade.session.dto.PnlResultDto;
import io.cryptobot.binance.trade.session.dto.SessionAllDto;
import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.model.TradeOrder;

import java.time.LocalDateTime;
import java.util.List;

public interface TradeSessionGetService {
    List<SessionAllDto> getAll();

    SessionDto getById(String idSession);

    List<SessionAllDto> getAllByPlan(String plan);

    List<SessionAllDto> getAllByStatus(SessionStatus status);

    List<TradeOrder> getOrders(String idSession);

    PnlResultDto calcPnlAll();

    PnlResultDto calcPnlByPlan(String plan);

    PnlResultDto calcPnlByTimeRange(LocalDateTime from, LocalDateTime to);

    PnlResultDto calcPnlByTimeRangeAndSymbol(LocalDateTime from, LocalDateTime to, String symbol);
}
package io.cryptobot.binance.trade.session.service.get;

import io.cryptobot.binance.trade.session.dao.TradeSessionRepository;
import io.cryptobot.binance.trade.session.dto.PnlResultDto;
import io.cryptobot.binance.trade.session.dto.SessionAllDto;
import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.exceptions.TradeSessionNotFoundException;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeSessionGetServiceImpl implements TradeSessionGetService {
    private final ModelMapper modelMapper;
    private final TradeSessionService tradeSessionService;
    private final TradeSessionRepository repository;

    @Override
    @Transactional
    public List<SessionAllDto> getAll() {
        List<TradeSession> all = repository.findAll();
        return all.stream()
                .map(a -> modelMapper.map(a, SessionAllDto.class))
                .toList();
    }

    @Override
    @Transactional
    public SessionDto getById(String idSession) {
        TradeSession session = repository.findById(idSession).orElseThrow(TradeSessionNotFoundException::new);
        return modelMapper.map(session, SessionDto.class);
    }

    @Override
    @Transactional
    public List<SessionAllDto> getAllByPlan(String plan) {
        List<TradeSession> all = repository.findAllByTradePlan(plan);
        return all.stream()
                .map(a -> modelMapper.map(a, SessionAllDto.class))
                .toList();
    }

    @Override
    @Transactional
    public List<SessionAllDto> getAllByStatus(SessionStatus status) {
        List<TradeSession> all = repository.findAllByStatus(status);
        return all.stream()
                .map(a -> modelMapper.map(a, SessionAllDto.class))
                .toList();
    }

    @Override
    @Transactional
    public PnlResultDto calcPnlAll() {
        List<TradeSession> all = repository.findAll();
        return calcPnlModel(all);
    }

    @Override
    @Transactional
    public PnlResultDto calcPnlByPlan(String plan) {
        List<TradeSession> all = repository.findAllByTradePlan(plan);
        return calcPnlModel(all);
    }

    private PnlResultDto calcPnlModel(List<TradeSession> all) {
        BigDecimal pnl = BigDecimal.ZERO;
        BigDecimal pnlTotal = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        for (TradeSession session : all) {
            pnl = pnl.add(session.getPnl());
            pnlTotal = pnlTotal.add(session.getPnl().subtract(session.getTotalCommission()));
            commission = commission.add(session.getTotalCommission());
        }
        return PnlResultDto.builder()
                .pnl(pnl)
                .pnlTotal(pnlTotal)
                .commission(commission)
                .build();
    }
}
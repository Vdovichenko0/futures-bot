package io.cryptobot.binance.trade.session.service;

import io.cryptobot.binance.trade.session.dao.TradeSessionRepository;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.exceptions.TradeSessionNotFoundException;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.trade_plan.service.update.TradePlanUpdateService;
import io.cryptobot.utils.LockType;
import io.cryptobot.utils.lock.single_lock.WithLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeSessionServiceImpl implements TradeSessionService {
    private final TradePlanUpdateService tradePlanUpdateService;
    private final TradeSessionRepository repository;

    @Override
    @Transactional
    public TradeSession create(String plan, TradingDirection direction, TradeOrder mainOrder, String context) {
        TradeSession session = new TradeSession();
        session.onCreate(plan, direction, mainOrder, context);
        repository.save(session);
        tradePlanUpdateService.setActiveTrue(plan, session.getId());
        return session;
    }

    @Override
    @Transactional
    public TradeSession getById(String idPlan) {
        return repository.findById(idPlan).orElseThrow(TradeSessionNotFoundException::new);
    }

    @Override
    @Transactional
    public List<TradeSession> getAllByPlan(String plan) {
        return repository.findAllByTradePlan(plan);
    }

    @Override
    @Transactional
    public List<TradeSession> getAllActive() {
        return repository.findAllByStatus(SessionStatus.ACTIVE);
    }

    @Override
    @Transactional
    public List<TradeSession> getAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.SESSION, keyParam = "idSession")
    public TradeSession addOrder(String idSession, TradeOrder order) {
        log.info("Adding order {} to session: {}", order.getOrderId(), idSession);

        TradeSession session = getById(idSession);

        if (order.getOrderId() == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }

        if (session.findOrderById(order.getOrderId()) != null) {
            log.warn("Order {} already exists in session {}", order.getOrderId(), idSession);
            return session;
        }

        session.addOrder(order);

        TradeSession savedSession = repository.save(session);
        log.info("Added order {} to session {}, new PnL: {}", order.getOrderId(), idSession, savedSession.getPnl());

        return savedSession;
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.SESSION, keyParam = "idSession")
    public TradeSession setMode(String idSession, SessionMode newMode) {
        log.info("Setting mode {} for session: {}", newMode, idSession);

        TradeSession session = getById(idSession);

        session.changeMode(newMode);

        TradeSession savedSession = repository.save(session);
        log.info("Changed mode to {} for session: {}", newMode, idSession);

        return savedSession;
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.SESSION, keyParam = "idSession")
    public TradeSession closeSession(String idSession) {
        log.info("Closing session: {}", idSession);

        TradeSession session = getById(idSession);

        if (SessionStatus.COMPLETED.equals(session.getStatus())) {
            log.warn("Session {} is already completed", idSession);
            return session;
        }

        session.completeSession();

        TradeSession savedSession = repository.save(session);
        log.info("Closed session: {}, final PnL: {}, duration: {} minutes", idSession, savedSession.getPnl(), savedSession.getDurationMinutes());

        tradePlanUpdateService.setActiveTrueFalse(savedSession.getTradePlan());
        return savedSession;
    }
}
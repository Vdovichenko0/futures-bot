package io.cryptobot.binance.trade.session.service.handle_work;

import io.cryptobot.binance.trade.session.dao.TradeSessionRepository;
import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.exceptions.TradeSessionNotFoundException;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.trade_plan.service.update.TradePlanUpdateService;
import io.cryptobot.binance.trading.monitoring.v3.MonitoringServiceV3;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.utils.LockType;
import io.cryptobot.utils.lock.single_lock.WithLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.order.enums.OrderPurpose;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionHandleActionsServiceImpl implements SessionHandleActionsService {
    private final ModelMapper modelMapper;
    private final TradeSessionRepository repository;
    private final MonitoringServiceV3 monitoringService;
    private final TradePlanUpdateService tradePlanUpdateService;
    private final TradingUpdatesService tradingUpdatesService;
    private final MonitorHelper monitorHelper;

    @Override
    @Transactional
    @WithLock(registry = LockType.SESSION, keyParam = "idSession")
    public SessionDto closeAllActiveOrders(String idSession) {
        //remove from monitoring
        //send request to close orders - all open
        //close session
        //add profits open plan

        TradeSession session = repository.findById(idSession).orElseThrow(TradeSessionNotFoundException::new);
        log.info("Closing ALL active orders for session {}", session.getId());

        monitoringService.removeFromMonitoring(idSession);

        TradeSession current = session;

        if (current.isActiveLong()) {
            TradeOrder longOrder = monitorHelper.getLatestActiveOrderByDirection(current, TradingDirection.LONG);
            if (longOrder != null) {
                OrderPurpose purpose = monitorHelper.determineCloseOrderPurpose(longOrder);
                TradeSession updated = tradingUpdatesService.closePosition(
                        current,
                        current.getCurrentMode(),
                        longOrder.getOrderId(),
                        longOrder.getRelatedHedgeId(),
                        longOrder.getDirection(),
                        purpose,
                        null,
                        "api_close_all_long"
                );
                if (updated != null) current = updated;
            }
        }

        if (current.isActiveShort()) {
            TradeOrder shortOrder = monitorHelper.getLatestActiveOrderByDirection(current, TradingDirection.SHORT);
            if (shortOrder != null) {
                OrderPurpose purpose = monitorHelper.determineCloseOrderPurpose(shortOrder);
                TradeSession updated = tradingUpdatesService.closePosition(
                        current,
                        current.getCurrentMode(),
                        shortOrder.getOrderId(),
                        shortOrder.getRelatedHedgeId(),
                        shortOrder.getDirection(),
                        purpose,
                        null,
                        "api_close_all_short"
                );
                if (updated != null) current = updated;
            }
        }

        return modelMapper.map(current, SessionDto.class);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.SESSION, keyParam = "idSession")
    public SessionDto closeSession(String idSession) {
        //remove from monitoring
        //close session
        //add profits open plan
        TradeSession session = repository.findById(idSession).orElseThrow(TradeSessionNotFoundException::new);
        monitoringService.removeFromMonitoring(idSession);
        session.completeSession();
        tradePlanUpdateService.setActiveFalse(session.getTradePlan());
        tradePlanUpdateService.addProfit(session.getTradePlan(), session.getPnl().subtract(session.getTotalCommission()).stripTrailingZeros());
        repository.save(session);

        return modelMapper.map(session, SessionDto.class);
    }

    @Override
    @Transactional
    @WithLock(registry = LockType.SESSION, keyParam = "idSession")
    public SessionDto closeOrderByDirection(String idSession, TradingDirection direction) {
        //remove from monitoring
        //create order
        //update session
        //add to monitoring if session not COMPLETED
        TradeSession session = repository.findById(idSession).orElseThrow(TradeSessionNotFoundException::new);
        monitoringService.removeFromMonitoring(idSession);
        
        if (!monitorHelper.isDirectionActive(session, direction)) {
            throw new IllegalStateException("No active position for direction: " + direction);
        }

        TradeOrder orderToClose = monitorHelper.getLatestActiveOrderByDirection(session, direction);
        if (orderToClose == null) {
            throw new IllegalStateException("Active order not found for direction: " + direction);
        }

//        SessionMode mode = session.hasBothPositionsActive() ? SessionMode.HEDGING : SessionMode.SCALPING;
        OrderPurpose purpose = monitorHelper.determineCloseOrderPurpose(orderToClose);

        TradeSession updatedSession = tradingUpdatesService.closePosition(
                session,
                session.getCurrentMode(),
                orderToClose.getOrderId(),
                orderToClose.getRelatedHedgeId(),
                orderToClose.getDirection(),
                purpose,
                null, // currentPrice not used in closePosition
                "api_close_by_direction"
        );

        if (updatedSession != null && updatedSession.getStatus().equals(SessionStatus.ACTIVE)) {
            monitoringService.addToMonitoring(updatedSession);
        }

        return modelMapper.map(updatedSession != null ? updatedSession : session, SessionDto.class);
    }
}

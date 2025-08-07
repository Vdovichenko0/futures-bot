package io.cryptobot.binance.trading.monitoring;

import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import io.cryptobot.binance.order.enums.OrderStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.order.enums.OrderPurpose;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringServiceImpl implements MonitoringService {
    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;
    private final TradingUpdatesService tradingUpdatesService;

    private final Map<String, TradeSession> sessions = new HashMap<>();

    @PostConstruct
    public void init() {
        List<TradeSession> ses = sessionService.getAllActive();
        for (TradeSession tradeSession : ses) {
            sessions.put(tradeSession.getId(), tradeSession);
        }

    }

    @Override
    public void addToMonitoring(TradeSession tradeSession) {
        sessions.put(tradeSession.getId(), tradeSession);
    }

    @Scheduled(fixedRate = 1_000)
    public void monitor() {
//        List<TradeSession>//todo
        Collection<TradeSession> activeSessions = sessions.values();

        for (TradeSession session : activeSessions) {
            try {
                monitorSession(session);
            } catch (Exception e) {
                log.error("❌ Error monitoring session {}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    private void monitorSession(TradeSession session) {
        log.debug("🔍 Starting monitoring for session {} (mode: {}, direction: {})",
                session.getId(), session.getCurrentMode(), session.getDirection());

        // Проверяем, не обрабатывается ли уже сессия
        if (session.isProcessing()) {
            log.debug("⏳ Session {} is already being processed, skipping", session.getId());
            return;
        }

        // 1. получаем цену
        BigDecimal price = ticker24hService.getPrice(session.getTradePlan());
        if (price == null) {
            log.warn("⚠️ Session {}: Failed to get price, skipping monitoring", session.getId());
            return;
        }
        log.debug("💰 Session {}: Current price = {}", session.getId(), price);

        // 2. получаем активный ордер для мониторинга
        TradeOrder activeOrder = getActiveOrderForMonitoring(session);
        if (activeOrder == null || activeOrder.getPrice() == null || activeOrder.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("⚠️ Session {}: No active order found for monitoring, skipping", session.getId());
            return;
        }
        BigDecimal entryPrice = activeOrder.getPrice();
        log.debug("📊 Session {}: Active order entry price = {} (orderId: {})", 
                session.getId(), entryPrice, activeOrder.getOrderId());

        // 3. PnL для активного ордера в процентах (точно как в Python)
        BigDecimal pnl;
        if (activeOrder.getDirection() == TradingDirection.LONG) {
            // LONG: ((current_price - entry_price) / entry_price * 100)
            pnl = price.subtract(entryPrice)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            } else {
            // SHORT: ((entry_price - current_price) / entry_price * 100)
            pnl = entryPrice.subtract(price)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        log.info("📈 Session {} ACTIVE PnL={}% (price: {}, entry: {})",
                session.getId(), pnl, price, entryPrice);

        // === ЛОГИКА ВЫБОРА РЕЖИМА (как в Python) ===
        boolean bothActive = session.hasBothPositionsActive();
        boolean anyActive = session.hasActivePosition();

        // Если есть обе позиции - режим двух позиций
        if (bothActive) {
            log.debug("🛡️ Session {}: Two positions active - HEDGING mode", session.getId());
            applyTwoPositionsLogic(session, price);
        }
        // Если есть только одна позиция - режим одной позиции
        else if (anyActive) {
            log.debug("🎯 Session {}: Single position active - SCALPING mode", session.getId());
            applySinglePositionLogic(session, price, activeOrder, pnl);
        }
        else {
            log.debug("⏳ Session {}: No active positions", session.getId());
        }



        log.debug("✅ Session {}: Monitoring cycle completed successfully", session.getId());
    }

    /**
     * Проверяет трейлинг для позиции
     */
    private boolean checkTrailing(TradeOrder order, BigDecimal currentPnl) {
        if (currentPnl.compareTo(order.getPnlHigh() != null ? order.getPnlHigh() : BigDecimal.ZERO) > 0) {
            order.setPnlHigh(currentPnl);
        }

        // Трейлинг активируется при PnL >= 0.15%
        if (currentPnl.compareTo(BigDecimal.valueOf(0.15)) >= 0 && !order.getTrailingActive()) {
            log.info("🚀 ACTIVATE trailing (PnL: {}%)", currentPnl);
            order.setTrailingActive(true);
            order.setPnlHigh(currentPnl);
            return false;
        }

        // Проверяем откат 20% от максимума
        if (order.getTrailingActive() && order.getPnlHigh() != null) {
            BigDecimal retrace = order.getPnlHigh().multiply(BigDecimal.valueOf(0.8));
            if (currentPnl.compareTo(retrace) <= 0) {
                log.info("📉 TRAILING RETRACE (high: {}%, current: {}%, retrace: {}%)",
                        order.getPnlHigh(),
                        currentPnl,
                        retrace);
                order.setTrailingActive(false);
                return true; // Нужно закрыть позицию
            }
        }

        return false;
    }

    /**
     * Запускает отслеживание позиции
     */
    private void startTracking(TradeOrder order, BigDecimal basePnl) {
        order.setBasePnl(basePnl);
        order.setMaxChangePnl(BigDecimal.ZERO);
        log.info("📊 Session {} → START TRACKING at {}%", order.getOrderId(), basePnl);
    }

    /**
     * Выполняет закрытие позиции и обновляет сессию
     */
    private void executeClosePosition(TradeSession session, TradeOrder orderToClose, SessionMode sessionMode, String reason) {
        try {
            // Помечаем сессию как обрабатываемую
            session.setProcessing(true);
            
            log.info("🔧 Executing close position for session {}: orderId={}, reason={}", 
                    session.getId(), orderToClose.getOrderId(), reason);
            
            // Определяем правильный OrderPurpose в зависимости от типа ордера
            OrderPurpose orderPurpose;
            if (orderToClose.getPurpose() == OrderPurpose.MAIN_OPEN) {
                orderPurpose = OrderPurpose.MAIN_CLOSE;
            } else if (orderToClose.getPurpose() == OrderPurpose.HEDGE_OPEN) {
                orderPurpose = OrderPurpose.HEDGE_CLOSE;
            } else {
                // По умолчанию используем HEDGE_CLOSE для безопасности
                orderPurpose = OrderPurpose.HEDGE_CLOSE;
                log.warn("⚠️ Unknown order purpose for order {}, using HEDGE_CLOSE", orderToClose.getOrderId());
            }
            
            // Выполняем закрытие позиции
            TradeSession updatedSession = tradingUpdatesService.closePosition(
                    session, sessionMode, orderToClose.getOrderId(), 
                    orderToClose.getRelatedHedgeId(), orderToClose.getDirection(), 
                    orderPurpose, ticker24hService.getPrice(session.getTradePlan()), 
                    "monitoring_" + reason
            );
            
            // Обновляем сессию в мониторинге
            updateSessionInMonitoring(updatedSession);
            
        } catch (Exception e) {
            log.error("❌ Error executing close position for session {}: {}", session.getId(), e.getMessage(), e);
            // Снимаем флаг обработки в случае ошибки
            session.setProcessing(false);
        }
    }

    /**
     * Выполняет открытие хеджа и обновляет сессию
     */
    private void executeOpenHedge(TradeSession session, TradingDirection hedgeDirection, String purpose, BigDecimal currentPrice, String reason) {
        try {
            // Помечаем сессию как обрабатываемую
            session.setProcessing(true);
            
            log.info("🔧 Executing open hedge for session {}: direction={}, reason={}", 
                    session.getId(), hedgeDirection, reason);
            
            // Выполняем открытие хеджа
            TradeSession updatedSession = tradingUpdatesService.openPosition(
                    session, SessionMode.HEDGING, hedgeDirection, 
                    OrderPurpose.HEDGE_OPEN, currentPrice, 
                    "monitoring_" + reason, session.getMainOrder().getOrderId(), null
            );
            
            // Обновляем сессию в мониторинге
            updateSessionInMonitoring(updatedSession);
            
        } catch (Exception e) {
            log.error("❌ Error executing open hedge for session {}: {}", session.getId(), e.getMessage(), e);
            // Снимаем флаг обработки в случае ошибки
            session.setProcessing(false);
        }
    }

    /**
     * Обновляет сессию в мониторинге
     */
    private void updateSessionInMonitoring(TradeSession updatedSession) {
        if (updatedSession != null) {
            if (updatedSession.getStatus() == SessionStatus.COMPLETED) {
                // Удаляем завершенную сессию из мониторинга
                sessions.remove(updatedSession.getId());
                log.info("✅ Session {} completed and removed from monitoring", updatedSession.getId());
            } else {
                // Обновляем сессию в мониторинге
                sessions.put(updatedSession.getId(), updatedSession);
                log.info("✅ Session {} updated in monitoring", updatedSession.getId());
            }
        }
    }

    /**
     * Получает активный ордер для мониторинга
     * Если основной ордер закрыт, но есть открытый хедж - возвращает хедж
     * Иначе возвращает основной ордер
     */
    private TradeOrder getActiveOrderForMonitoring(TradeSession session) {
        // Определяем активный ордер на основе состояния сессии, а не наличия цены у ордера
        boolean activeLong = session.isActiveLong();
        boolean activeShort = session.isActiveShort();

        if (!activeLong && !activeShort) {
            return null;
        }

        // Если активна только LONG позиция
        if (activeLong && !activeShort) {
            if (session.getDirection() == TradingDirection.LONG) {
                return session.getMainOrder();
            } else {
                return getLastFilledHedgeOrderByDirection(session, TradingDirection.LONG);
            }
        }

        // Если активна только SHORT позиция
        if (activeShort && !activeLong) {
            if (session.getDirection() == TradingDirection.SHORT) {
                return session.getMainOrder();
            } else {
                return getLastFilledHedgeOrderByDirection(session, TradingDirection.SHORT);
            }
        }

        // Если активны обе — для мониторинга вернем основной
        return session.getMainOrder();
    }

    private TradeOrder getLastFilledHedgeOrderByDirection(TradeSession session, TradingDirection direction) {
        return session.getOrders().stream()
                .filter(o -> OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .filter(o -> direction.equals(o.getDirection()))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    /**
     * Проверяет, есть ли активные позиции для мониторинга
     * (как в Python: active_positions["long"] и active_positions["short"])
     */
    private boolean hasActivePositions(TradeSession session) {
        TradeOrder mainOrder = session.getMainOrder();
        TradeOrder hedgeOrder = session.getLastHedgeOrder();
        
        boolean hasMain = mainOrder != null && mainOrder.getPrice() != null && mainOrder.getPrice().compareTo(BigDecimal.ZERO) > 0;
        boolean hasHedge = hedgeOrder != null && hedgeOrder.getPrice() != null && hedgeOrder.getPrice().compareTo(BigDecimal.ZERO) > 0;
        
        return hasMain || hasHedge;
    }

    /**
     * Логика для одной позиции (как в Python apply_single_position_logic)
     */
    private void applySinglePositionLogic(TradeSession session, BigDecimal price, TradeOrder activeOrder, BigDecimal pnl) {
        // === ТРЕЙЛИНГ ===
        if (checkTrailing(activeOrder, pnl)) {
            log.info("📉 Session {} → CLOSE POSITION (TRAILING)", session.getId());
            executeClosePosition(session, activeOrder, SessionMode.SCALPING, "TRAILING");
            return;
        }

                            // === ОТСЛЕЖИВАНИЕ включается только если PnL <= -0.3% ===
        if (pnl.compareTo(BigDecimal.valueOf(-0.3)) > 0) {
            // Сбрасываем tracking если PnL > -0.3%
            activeOrder.setBasePnl(null);
            activeOrder.setMaxChangePnl(null);
        } else {
            // === Если tracking ещё не активен — запускаем ===
            if (activeOrder.getBasePnl() == null) {
                startTracking(activeOrder, pnl);
            }

            // Проверяем, что tracking действительно активен
            if (activeOrder.getBasePnl() == null) {
                return;
            }

            BigDecimal delta = pnl.subtract(activeOrder.getBasePnl());

            // ✅ Ухудшение -0.1% → открываем хедж (только если нет второй позиции)
            if (delta.compareTo(BigDecimal.valueOf(-0.1)) <= 0) {
                // Проверяем, есть ли уже две активные позиции (как в Python)
                if (session.hasBothPositionsActive()) {
                    log.warn("⚠️ Session {} → HEDGE BLOCKED (two positions already active)", session.getId());
                    return;
                }
                
                log.info("🛡️ Session {} → OPEN HEDGE (worsening {}% from {}%)",
                        session.getId(), delta,
                        activeOrder.getBasePnl());
                TradingDirection hedgeDirection = getOppositeDirection(activeOrder.getDirection());
                executeOpenHedge(session, hedgeDirection, "HEDGE_OPEN", price, "worsening");
                return;
            }

            // ✅ Фиксируем максимум улучшения
            if (delta.compareTo(activeOrder.getMaxChangePnl() != null ? activeOrder.getMaxChangePnl() : BigDecimal.ZERO) > 0) {
                activeOrder.setMaxChangePnl(delta);
            }

                            // ✅ Улучшение > +0.1% и откат ≥30% → хедж (только если нет второй позиции)
                BigDecimal maxImp = activeOrder.getMaxChangePnl() != null ? activeOrder.getMaxChangePnl() : BigDecimal.ZERO;
                if (maxImp.compareTo(BigDecimal.valueOf(0.1)) > 0 &&
                        delta.compareTo(maxImp.multiply(BigDecimal.valueOf(0.7))) <= 0) {
                // Проверяем, есть ли уже две активные позиции (как в Python)
                if (session.hasBothPositionsActive()) {
                    log.warn("⚠️ Session {} → HEDGE BLOCKED (two positions already active)", session.getId());
                    return;
                }
                
                log.info("🛡️ Session {} → OPEN HEDGE (improvement {}%, pullback ≥30%)",
                        session.getId(), maxImp);
                TradingDirection hedgeDirection = getOppositeDirection(activeOrder.getDirection());
                executeOpenHedge(session, hedgeDirection, "HEDGE_OPEN", price, "improvement");
                return;
            }
        }
    }

    /**
     * Логика для двух позиций (как в Python apply_two_positions_logic)
     */
    private void applyTwoPositionsLogic(TradeSession session, BigDecimal price) {
        // Получаем основной ордер и хедж
        TradeOrder mainOrder = session.getMainOrder();
        TradeOrder hedgeOrder = session.getLastHedgeOrder();
        
        if (mainOrder == null || mainOrder.getPrice() == null || mainOrder.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("⚠️ Session {}: Main order is null, has no price, or price is zero, skipping hedge monitoring", session.getId());
            return;
        }
        
        if (hedgeOrder == null || hedgeOrder.getPrice() == null || hedgeOrder.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("⚠️ Session {}: Hedge order is null, has no price, or price is zero, skipping hedge monitoring", session.getId());
            return;
        }
        
        BigDecimal entryMain = mainOrder.getPrice();
        BigDecimal entryHedge = hedgeOrder.getPrice();

        // PnL для main в процентах (точно как в Python)
        BigDecimal pnlMain;
        if (mainOrder.getDirection() == TradingDirection.LONG) {
            // LONG: ((current_price - entry_price) / entry_price * 100)
            pnlMain = price.subtract(entryMain)
                    .divide(entryMain, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            // SHORT: ((entry_price - current_price) / entry_price * 100)
            pnlMain = entryMain.subtract(price)
                    .divide(entryMain, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // PnL для hedge в процентах (точно как в Python)
        BigDecimal pnlHedge;
        if (hedgeOrder.getDirection() == TradingDirection.LONG) {
            // LONG: ((current_price - entry_price) / entry_price * 100)
            pnlHedge = price.subtract(entryHedge)
                    .divide(entryHedge, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            // SHORT: ((entry_price - current_price) / entry_price * 100)
            pnlHedge = entryHedge.subtract(price)
                    .divide(entryHedge, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // Определяем лучшую и худшую позиции (как в Python)
        String bestDirection = pnlMain.compareTo(pnlHedge) > 0 ? "MAIN" : "HEDGE";
        String worstDirection = pnlMain.compareTo(pnlHedge) > 0 ? "HEDGE" : "MAIN";
        BigDecimal bestPnl = pnlMain.compareTo(pnlHedge) > 0 ? pnlMain : pnlHedge;
        BigDecimal worstPnl = pnlMain.compareTo(pnlHedge) > 0 ? pnlHedge : pnlMain;
        TradeOrder bestOrder = pnlMain.compareTo(pnlHedge) > 0 ? mainOrder : hedgeOrder;
        TradeOrder worstOrder = pnlMain.compareTo(pnlHedge) > 0 ? hedgeOrder : mainOrder;

        log.info("🏆 Session {}: BEST={} {}% | WORST={} {}%",
                session.getId(), bestDirection, bestPnl,
                worstDirection, worstPnl);

        // === ТРЕЙЛИНГ для лучшей позиции ===
        if (checkTrailing(bestOrder, bestPnl)) {
            log.info("📉 Session {} → CLOSE {} (TRAILING BEST)", session.getId(), bestDirection);
            executeClosePosition(session, bestOrder, SessionMode.HEDGING, "TRAILING_BEST");
            
            // После закрытия лучшей позиции запускаем отслеживание худшей
            if (worstOrder.getBasePnl() == null) {
                startTracking(worstOrder, worstPnl);
            }
            return;
        }

        // Пока активны обе позиции — блокируем открытие новых хеджей (как в Python)
        if (session.hasBothPositionsActive()) {
            log.warn("⚠️ Session {} → HEDGE BLOCKED (two positions already active)", session.getId());
            return;
        }

        // === Если worst снова в зоне отслеживания ===
        if (worstPnl.compareTo(BigDecimal.valueOf(-0.3)) <= 0 && worstOrder.getBasePnl() == null) {
            startTracking(worstOrder, worstPnl);
        }

        // Если tracking не активен — выходим
        if (worstOrder.getBasePnl() == null) {
            return;
        }

        BigDecimal delta = worstPnl.subtract(worstOrder.getBasePnl());

        // ✅ Ухудшение WORST на -0.1% → открываем хедж (только если одна позиция)
        if (delta.compareTo(BigDecimal.valueOf(-0.1)) <= 0) {
            TradingDirection oppositeDirection = getOppositeDirection(worstOrder.getDirection());

            // ❗ Защита от повторного открытия того же направления (как в Python)
            boolean hasOppositePosition =
                    (session.isActiveLong() && oppositeDirection == TradingDirection.LONG) ||
                    (session.isActiveShort() && oppositeDirection == TradingDirection.SHORT);

            if (hasOppositePosition) {
                log.warn("⚠️ Session {} → RE-HEDGE BLOCKED (opposite position already active)", session.getId());
                return;
            }

            log.info("🛡️ Session {} → RE-HEDGE {} (worsening WORST {}% from {}%)",
                    session.getId(), oppositeDirection, delta, worstOrder.getBasePnl());
            executeOpenHedge(session, oppositeDirection, "HEDGE_OPEN", price, "re_hedge_worsening");
            return;
        }

                    // ✅ Фиксируем максимум улучшения
            if (delta.compareTo(worstOrder.getMaxChangePnl() != null ? worstOrder.getMaxChangePnl() : BigDecimal.ZERO) > 0) {
                worstOrder.setMaxChangePnl(delta);
            }

        // ✅ Улучшение > +0.1% и откат ≥30% → открываем хедж (только если одна позиция)
        BigDecimal maxImp = worstOrder.getMaxChangePnl() != null ? worstOrder.getMaxChangePnl() : BigDecimal.ZERO;
        if (maxImp.compareTo(BigDecimal.valueOf(0.1)) > 0 &&
                delta.compareTo(maxImp.multiply(BigDecimal.valueOf(0.7))) <= 0) {
            TradingDirection oppositeDirection = getOppositeDirection(worstOrder.getDirection());

            // ❗ Защита от повторного открытия того же направления (как в Python)
            boolean hasOppositePosition =
                    (session.isActiveLong() && oppositeDirection == TradingDirection.LONG) ||
                    (session.isActiveShort() && oppositeDirection == TradingDirection.SHORT);

            if (hasOppositePosition) {
                log.warn("⚠️ Session {} → RE-HEDGE BLOCKED (opposite position already active)", session.getId());
                return;
            }

            log.info("🛡️ Session {} → RE-HEDGE {} (improvement WORST {}%, pullback ≥30%)",
                    session.getId(), oppositeDirection, maxImp);
            executeOpenHedge(session, oppositeDirection, "HEDGE_OPEN", price, "re_hedge_improvement");
            return;
        }
    }

    private TradingDirection getOppositeDirection(TradingDirection direction) {
        return direction == TradingDirection.LONG ? TradingDirection.SHORT : TradingDirection.LONG;
    }
}

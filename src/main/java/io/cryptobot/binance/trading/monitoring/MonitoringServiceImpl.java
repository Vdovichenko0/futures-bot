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
    
    // === КОНСТАНТЫ МОНИТОРИНГА ===
    
    // Интервалы и таймауты
    private static final long MONITORING_INTERVAL_MS = 1_000;                   // Интервал мониторинга 1 сек
    private static final long ORDER_COOLDOWN_MS = 10_000;                       // Кулдаун между ордерами 10 сек
    
    // PnL пороги и коэффициенты
    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);          // Множитель для процентов
    private static final BigDecimal TRAILING_ACTIVATION_THRESHOLD = BigDecimal.valueOf(0.15); // Активация трейлинга 0.15%
    private static final BigDecimal TRAILING_RETRACE_RATIO = BigDecimal.valueOf(0.8);         // Откат трейлинга 80%
    private static final BigDecimal TRACKING_ACTIVATION_THRESHOLD = BigDecimal.valueOf(-0.3);     // Активация отслеживания -0.3%
    private static final BigDecimal WORSENING_THRESHOLD = BigDecimal.valueOf(-0.1);               // Порог ухудшения -0.1%
    private static final BigDecimal IMPROVEMENT_THRESHOLD = BigDecimal.valueOf(0.1);          // Порог улучшения 0.1%
    private static final BigDecimal PULLBACK_RATIO = BigDecimal.valueOf(0.7);                 // Соотношение отката 70%
    
    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;
    private final TradingUpdatesService tradingUpdatesService;

    private final Map<String, TradeSession> sessions = new HashMap<>();
    // Per-session order cooldown to avoid rapid consecutive open/close calls
    private final Map<String, Long> lastOrderAtMsBySession = new HashMap<>();

    // @PostConstruct
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

    @Scheduled(fixedRate = MONITORING_INTERVAL_MS)
    public void monitor() {
//        List<TradeSession>
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
        log.debug("🔍 Starting monitoring for session {} (mode: {}, direction: {})", session.getId(), session.getCurrentMode(), session.getDirection());

        // Проверяем, не обрабатывается ли уже сессия
        if (session.isProcessing()) {
            log.debug("⏳ Session {} is already being processed, skipping", session.getId());
            return;
        }

        // 1. получаем цену
        BigDecimal price = ticker24hService.getPrice(session.getTradePlan());
        if (price == null) {
//            log.warn("⚠️ Session {}: Failed to get price, skipping monitoring", session.getId());
            return;
        }
        // log.debug("💰 Session {}: Current price = {}", session.getId(), price);

        // === ЛОГИКА ВЫБОРА РЕЖИМА (как в Python) ===
        boolean bothActive = session.hasBothPositionsActive();
        boolean anyActive = session.hasActivePosition();

        // Если есть обе позиции - режим двух позиций (важно обрабатывать ДО поиска activeOrder)
        if (bothActive) {
            log.debug("🛡️ Session {}: Two positions active - HEDGING mode", session.getId());
            applyTwoPositionsLogic(session, price);
            return;
        }

        // Если есть только одна позиция - режим одной позиции
        if (anyActive) {
            // 2. получаем активный ордер для мониторинга
            TradeOrder activeOrder = getActiveOrderForMonitoring(session);
            if (activeOrder == null || activeOrder.getPrice() == null || activeOrder.getPrice().compareTo(BigDecimal.ZERO) == 0) {
                log.warn("⚠️ Session {}: No active order found for monitoring, skipping", session.getId());
                return;
            }
            BigDecimal entryPrice = activeOrder.getPrice();

            // 3. PnL для активного ордера в процентах (точно как в Python)
            BigDecimal pnl;
            if (activeOrder.getDirection() == TradingDirection.LONG) {
                // LONG: ((current_price - entry_price) / entry_price * 100)
                pnl = price.subtract(entryPrice)
                        .divide(entryPrice, 8, RoundingMode.HALF_UP)
                        .multiply(PERCENTAGE_MULTIPLIER);
            } else {
                // SHORT: ((entry_price - current_price) / entry_price * 100)
                pnl = entryPrice.subtract(price)
                        .divide(entryPrice, 8, RoundingMode.HALF_UP)
                        .multiply(PERCENTAGE_MULTIPLIER);
            }

            log.debug("🎯 Session {}: Single position active - SCALPING mode", session.getId());
            applySinglePositionLogic(session, price, activeOrder, pnl);
            return;
        }

        log.debug("⏳ Session {}: No active positions", session.getId());



        // log.debug("✅ Session {}: Monitoring cycle completed successfully", session.getId());
    }

    /**
     * Проверяет трейлинг для позиции
     */
    private boolean checkTrailing(TradeOrder order, BigDecimal currentPnl) {
        if (currentPnl.compareTo(order.getPnlHigh() != null ? order.getPnlHigh() : BigDecimal.ZERO) > 0) {
            order.setPnlHigh(currentPnl);
        }

        // Трейлинг активируется при PnL >= 0.15%
        if (currentPnl.compareTo(TRAILING_ACTIVATION_THRESHOLD) >= 0 && !order.getTrailingActive()) {
            log.info("🚀 ACTIVATE trailing (PnL: {}%)", currentPnl);
            order.setTrailingActive(true);
            order.setPnlHigh(currentPnl);
            return false;
        }

        // Проверяем откат 20% от максимума
        if (order.getTrailingActive() && order.getPnlHigh() != null) {
            BigDecimal retrace = order.getPnlHigh().multiply(TRAILING_RETRACE_RATIO);
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
            // Cooldown: avoid spamming orders every monitor tick
            if (isInOrderCooldown(session.getId())) {
                log.warn("⏱️ Session {} → ORDER COOLDOWN active, skip close {}", session.getId(), orderToClose.getOrderId());
                return;
            }
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
            
            // Ставим метку кулдауна немедленно, чтобы не заспамить при сбое
            markOrderSent(session.getId());
            
            // Выполняем закрытие позиции
            TradeSession updatedSession = tradingUpdatesService.closePosition(
                    session, sessionMode, orderToClose.getOrderId(), 
                    orderToClose.getRelatedHedgeId(), orderToClose.getDirection(), 
                    orderPurpose, ticker24hService.getPrice(session.getTradePlan()), 
                    reason
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
            // Cooldown: avoid spamming orders every monitor tick
            if (isInOrderCooldown(session.getId())) {
                log.warn("⏱️ Session {} → ORDER COOLDOWN active, skip open hedge {}", session.getId(), hedgeDirection);
                return;
            }
            // Помечаем сессию как обрабатываемую
            session.setProcessing(true);
            
            log.info("🔧 Executing open hedge for session {}: direction={}, reason={}", 
                    session.getId(), hedgeDirection, reason);
            
            // Выбираем корректный parentOrderId: если main закрыт/отсутствует, привязываем к актуальному ордеру противоположной стороны
            Long parentOrderId = null;
            TradeOrder mainOrder = session.getMainOrder();
            if (mainOrder != null && mainOrder.getOrderId() != null && isMainStillActive(session)) {
                parentOrderId = mainOrder.getOrderId();
            } else {
                TradeOrder link = getLastFilledHedgeOrderByDirection(session, getOppositeDirection(hedgeDirection));
                if (link != null) parentOrderId = link.getOrderId();
            }

            // Ставим метку кулдауна немедленно, чтобы не заспамить при сбое
            markOrderSent(session.getId());
            
            // Выполняем открытие хеджа
            TradeSession updatedSession = tradingUpdatesService.openPosition(
                    session, SessionMode.HEDGING, hedgeDirection, 
                    OrderPurpose.HEDGE_OPEN, currentPrice, 
                    reason, parentOrderId, null
            );
            
            // Обновляем сессию в мониторинге
            updateSessionInMonitoring(updatedSession);
            
        } catch (Exception e) {
            log.error("❌ Error executing open hedge for session {}: {}", session.getId(), e.getMessage(), e);
            // Снимаем флаг обработки в случае ошибки
            session.setProcessing(false);
        }
    }

    private boolean isMainStillActive(TradeSession session) {
        TradeOrder main = session.getMainOrder();
        if (main == null) return false;

        // Если флаги сессии говорят, что сторона main уже не активна — main закрыт
        if (main.getDirection() == TradingDirection.LONG && !session.isActiveLong()) {
            return false;
        }
        if (main.getDirection() == TradingDirection.SHORT && !session.isActiveShort()) {
            return false;
        }

        // Явная проверка наличия FILLED MAIN_CLOSE/MAIN_PARTIAL_CLOSE для main
        boolean hasMainClose = session.getOrders().stream()
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .anyMatch(o -> (
                        OrderPurpose.MAIN_CLOSE.equals(o.getPurpose()) ||
                        OrderPurpose.MAIN_PARTIAL_CLOSE.equals(o.getPurpose())
                ) && main.getOrderId().equals(o.getParentOrderId()));

        return !hasMainClose;
    }

    private boolean isInOrderCooldown(String sessionId) {
        Long last = lastOrderAtMsBySession.get(sessionId);
        if (last == null) return false;
        return System.currentTimeMillis() - last < ORDER_COOLDOWN_MS;
    }

    private void markOrderSent(String sessionId) {
        lastOrderAtMsBySession.put(sessionId, System.currentTimeMillis());
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
                            String context = String.format("monitoring_trailing pnl>=%.3f retrace<=%.3f", 
                        activeOrder.getPnlHigh(), activeOrder.getPnlHigh().multiply(TRAILING_RETRACE_RATIO));
                executeClosePosition(session, activeOrder, SessionMode.SCALPING, context);
            return;
        }

                            // === ОТСЛЕЖИВАНИЕ включается только если PnL <= -0.3% ===
        if (pnl.compareTo(TRACKING_ACTIVATION_THRESHOLD) > 0) {
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
            if (delta.compareTo(WORSENING_THRESHOLD) <= 0) {
                // Проверяем, есть ли уже две активные позиции (как в Python)
                if (session.hasBothPositionsActive()) {
                    // log.warn("⚠️ Session {} → HEDGE BLOCKED (two positions already active)", session.getId());
                    return;
                }

                TradingDirection hedgeDirection = getOppositeDirection(activeOrder.getDirection());
                // Жёсткий предохранитель: не открывать хедж в сторону, которая уже активна
                if ((hedgeDirection == TradingDirection.LONG && session.isActiveLong()) ||
                        (hedgeDirection == TradingDirection.SHORT && session.isActiveShort())) {
                    // log.warn("⚠️ Session {} → HEDGE BLOCKED (target direction already active)", session.getId());
                    return;
                }
                
                String context = String.format("monitoring_worsening base<=%.3f delta<=%.3f pnl=%.3f", 
                        activeOrder.getBasePnl(), delta, pnl);
                log.info("🛡️ Session {} → OPEN HEDGE (reason=worsening, basePnl={}%, delta={}%, pnl={}%)",
                        session.getId(), activeOrder.getBasePnl(), delta, pnl);
                executeOpenHedge(session, hedgeDirection, "HEDGE_OPEN", price, context);
                return;
            }

            // ✅ Фиксируем максимум улучшения
            if (delta.compareTo(activeOrder.getMaxChangePnl() != null ? activeOrder.getMaxChangePnl() : BigDecimal.ZERO) > 0) {
                activeOrder.setMaxChangePnl(delta);
            }

                            // ✅ Улучшение > +0.1% и откат ≥30% → хедж (только если нет второй позиции)
                BigDecimal maxImp = activeOrder.getMaxChangePnl() != null ? activeOrder.getMaxChangePnl() : BigDecimal.ZERO;
                if (maxImp.compareTo(IMPROVEMENT_THRESHOLD) > 0 &&
                        delta.compareTo(maxImp.multiply(PULLBACK_RATIO)) <= 0) {
                // Проверяем, есть ли уже две активные позиции (как в Python)
                if (session.hasBothPositionsActive()) {
                    log.warn("⚠️ Session {} → HEDGE BLOCKED (two positions already active)", session.getId());
                    return;
                }

                TradingDirection hedgeDirection = getOppositeDirection(activeOrder.getDirection());
                // Жёсткий предохранитель: не открывать хедж в сторону, которая уже активна
                if ((hedgeDirection == TradingDirection.LONG && session.isActiveLong()) ||
                        (hedgeDirection == TradingDirection.SHORT && session.isActiveShort())) {
                    log.warn("⚠️ Session {} → HEDGE BLOCKED (target direction already active)", session.getId());
                    return;
                }
                
                String context = String.format("monitoring_improvement maxImp>%.3f pullback>=30%% delta=%.3f pnl=%.3f", 
                        maxImp, delta, pnl);
                log.info("🛡️ Session {} → OPEN HEDGE (reason=improvement_pullback, maxImp={}%, delta={}%, pnl={}%)",
                        session.getId(), maxImp, delta, pnl);
                executeOpenHedge(session, hedgeDirection, "HEDGE_OPEN", price, context);
                return;
            }
        }
    }

    /**
     * Логика для двух позиций (как в Python apply_two_positions_logic)
     */
    private void applyTwoPositionsLogic(TradeSession session, BigDecimal price) {
        // Поддерживаем режим двух позиций даже если основной ордер уже закрыт:
        // берем последние активные ордера по направлениям LONG и SHORT (MAIN_OPEN или HEDGE_OPEN, FILLED, не закрытые)
        TradeOrder longOrder = getLatestActiveOrderByDirection(session, TradingDirection.LONG);
        TradeOrder shortOrder = getLatestActiveOrderByDirection(session, TradingDirection.SHORT);

        if (longOrder == null || shortOrder == null) {
            log.warn("⚠️ Session {}: Missing active LONG or SHORT for two-positions logic", session.getId());
            return;
        }

        BigDecimal entryLong = longOrder.getPrice();
        BigDecimal entryShort = shortOrder.getPrice();

        // PnL для LONG
        BigDecimal pnlLong = price.subtract(entryLong)
                .divide(entryLong, 8, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER);
        // PnL для SHORT
        BigDecimal pnlShort = entryShort.subtract(price)
                .divide(entryShort, 8, RoundingMode.HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER);

        // Определяем лучшую и худшую позиции
        boolean longIsBest = pnlLong.compareTo(pnlShort) > 0;
        String bestDirection = longIsBest ? "LONG" : "SHORT";
        String worstDirection = longIsBest ? "SHORT" : "LONG";
        BigDecimal bestPnl = longIsBest ? pnlLong : pnlShort;
        BigDecimal worstPnl = longIsBest ? pnlShort : pnlLong;
        TradeOrder bestOrder = longIsBest ? longOrder : shortOrder;
        TradeOrder worstOrder = longIsBest ? shortOrder : longOrder;

//        log.info("🏆 Session {}: BEST={} {}% | WORST={} {}%",
//                session.getId(), bestDirection, bestPnl,
//                worstDirection, worstPnl);

        // === ТРЕЙЛИНГ для лучшей позиции ===
        if (checkTrailing(bestOrder, bestPnl)) {
            log.info("📉 Session {} → CLOSE {} (TRAILING BEST)", session.getId(), bestDirection);
                            String context = String.format("monitoring_trailing_best bestPnl=%.3f retrace<=%.3f", 
                        bestPnl, bestPnl.multiply(TRAILING_RETRACE_RATIO));
                executeClosePosition(session, bestOrder, SessionMode.HEDGING, context);
            
            // После закрытия лучшей позиции запускаем отслеживание худшей
            if (worstOrder.getBasePnl() == null) {
                startTracking(worstOrder, worstPnl);
            }
            return;
        }

        // Пока активны обе позиции — блокируем открытие новых хеджей (как в Python)
        if (session.hasBothPositionsActive()) {
//            log.warn("⚠️ Session {} → HEDGE BLOCKED (two positions already active)", session.getId());
            return;
        }

        // === Если worst снова в зоне отслеживания ===
        if (worstPnl.compareTo(TRACKING_ACTIVATION_THRESHOLD) <= 0 && worstOrder.getBasePnl() == null) {
            startTracking(worstOrder, worstPnl);
        }

        // Если tracking не активен — выходим
        if (worstOrder.getBasePnl() == null) {
            return;
        }

        BigDecimal delta = worstPnl.subtract(worstOrder.getBasePnl());

        // ✅ Ухудшение WORST на -0.1% → открываем хедж (только если одна позиция)
        if (delta.compareTo(WORSENING_THRESHOLD) <= 0) {
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
        if (maxImp.compareTo(IMPROVEMENT_THRESHOLD) > 0 &&
                delta.compareTo(maxImp.multiply(PULLBACK_RATIO)) <= 0) {
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

    private TradeOrder getLatestActiveOrderByDirection(TradeSession session, TradingDirection direction) {
        // Находим последний FILLED открывающий ордер нужного направления, который не закрыт
        return session.getOrders().stream()
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .filter(o -> direction.equals(o.getDirection()))
                .filter(o -> OrderPurpose.MAIN_OPEN.equals(o.getPurpose()) || OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .filter(o -> !isOpenOrderClosed(session, o))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    private boolean isOpenOrderClosed(TradeSession session, TradeOrder openOrder) {
        if (OrderPurpose.MAIN_OPEN.equals(openOrder.getPurpose())) {
            return session.getOrders().stream()
                    .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                    .anyMatch(o -> (OrderPurpose.MAIN_CLOSE.equals(o.getPurpose()) || OrderPurpose.MAIN_PARTIAL_CLOSE.equals(o.getPurpose()))
                            && openOrder.getOrderId().equals(o.getParentOrderId()));
        }
        if (OrderPurpose.HEDGE_OPEN.equals(openOrder.getPurpose())) {
            return session.getOrders().stream()
                    .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                    .anyMatch(o -> (OrderPurpose.HEDGE_CLOSE.equals(o.getPurpose()) || OrderPurpose.HEDGE_PARTIAL_CLOSE.equals(o.getPurpose()))
                            && openOrder.getOrderId().equals(o.getParentOrderId()));
        }
        return true;
    }
}

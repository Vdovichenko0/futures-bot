package io.cryptobot.binance.trading.monitoring.v3;

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
import java.util.stream.Collectors;

import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.order.enums.OrderPurpose;

/**
 * MonitoringServiceV3Impl — Новая стратегия мониторинга V3
 * <p>
 * Состояния и переходы:
 * <p>
 * 1) Одна позиция (SCALPING):
 * - Трейлинг: при достижении порога +TRAILING_ACTIVATION_THRESHOLD_PCT активируем трейлинг.
 * Закрываем по откату TRAILING_CLOSE_RETRACE_RATIO от максимума PnL.
 * - Хедж: при падении до SINGLE_POSITION_HEDGE_THRESHOLD_PCT открываем хедж в противоположную сторону.
 * <p>
 * 2) Две позиции (HEDGING):
 * - Если любая позиция достигла +TWO_POS_PROFITABLE_ACTIVATION_PCT:
 * включаем трейлинг на прибыльной (закрытие по откату) и запускаем сопровождение убыточной (follow-up).
 * <p>
 * 3) Сопровождение убыточной (follow-up) после закрытия прибыльной:
 * - Базовая точка baseline фиксируется в момент перехода к одной позиции.
 * - Если убыточная ухудшилась на FOLLOW_UP_WORSEN_DELTA_PCT от baseline — открываем хедж.
 * - Если убыточная улучшилась на FOLLOW_UP_IMPROVE_DELTA_PCT — включаем ей трейлинг и открываем хедж.
 * - Если убыточная достигла 1/3 опорной прибыли пары (FOLLOW_UP_ONE_THIRD_PROFIT_RATIO от profitReferencePnl)
 * — закрываем убыточную и продолжаем скальпинг.
 * <p>
 * Все ключевые пороги и коэффициенты вынесены в константы выше для удобной настройки.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringServiceV3Impl implements MonitoringServiceV3 {

    // === КОНСТАНТЫ МОНИТОРИНГА И СТРАТЕГИИ (ЛЕГКО МЕНЯТЬ) ===

    // Интервалы и таймауты
    private static final long MONITORING_INTERVAL_MS = 1_000;                   // Интервал мониторинга 1 сек
    private static final long ORDER_COOLDOWN_MS = 10_000;                       // Кулдаун между ордерами 10 сек

    // PnL пороги и коэффициенты
    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);          // Множитель для процентов

    // Трейлинг
    private static final BigDecimal TRAILING_ACTIVATION_THRESHOLD_PCT = BigDecimal.valueOf(0.20); // Активация трейлинга при +0.2%
    private static final BigDecimal TRAILING_CLOSE_RETRACE_RATIO = BigDecimal.valueOf(0.60);      // Закрытие при 40% отката от максимума

    // Хедж при одной позиции
    private static final BigDecimal SINGLE_POSITION_HEDGE_THRESHOLD_PCT = BigDecimal.valueOf(-0.20); // Хедж при -0.2%

    // Две позиции: активация трейлинга для прибыльной при достижении порога
    private static final BigDecimal TWO_POS_PROFITABLE_ACTIVATION_PCT = BigDecimal.valueOf(0.20);    // Любая достигла +0.1% — трейлим и закрываем

    // Сопровождение убыточной после закрытия прибыльной
    private static final BigDecimal FOLLOW_UP_WORSEN_DELTA_PCT = BigDecimal.valueOf(-0.10); // Ухудшилась на -0.1% — хедж
    private static final BigDecimal FOLLOW_UP_IMPROVE_DELTA_PCT = BigDecimal.valueOf(0.10); // Улучшилась на +0.1% — включаем трейлинг и открываем хедж
    private static final BigDecimal FOLLOW_UP_ONE_THIRD_PROFIT_RATIO = new BigDecimal("0.3333"); // Закрыть убыточную при достижении 1/3 прибыли пары

    // Старые параметры (удалены, так как не используются в новой логике)

    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;
    private final TradingUpdatesService tradingUpdatesService;

    private final Map<String, TradeSession> sessions = new HashMap<>();
    // Per-session order cooldown to avoid rapid consecutive open/close calls
    private final Map<String, Long> lastOrderAtMsBySession = new HashMap<>();
    // Сопровождение убыточной позиции после закрытия прибыльной
    private final Map<String, FollowUpState> followUpBySession = new HashMap<>();

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

        log.debug("🔍 SESSION LOGIC DEBUG: Session {} - bothActive={}, anyActive={}, activeLong={}, activeShort={}",
                session.getId(), bothActive, anyActive, session.isActiveLong(), session.isActiveShort());

        // Если есть обе позиции - режим двух позиций (важно обрабатывать ДО поиска activeOrder)
        if (bothActive) {
//            log.info("🛡️ Session {}: Two positions active - HEDGING mode", session.getId());
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
     * Проверяет трейлинг для позиции (НОВАЯ ЛОГИКА)
     */
    private boolean checkNewTrailing(TradeOrder order, BigDecimal currentPnl) {
        // Обновляем максимальный PnL
        if (currentPnl.compareTo(order.getPnlHigh() != null ? order.getPnlHigh() : BigDecimal.ZERO) > 0) {
            order.setPnlHigh(currentPnl);
        }

        // Трейлинг активируется при PnL >= 0.1%
        boolean isTrailingActive = order.getTrailingActive() != null && order.getTrailingActive();
        if (currentPnl.compareTo(TRAILING_ACTIVATION_THRESHOLD_PCT) >= 0 && !isTrailingActive) {
            log.info("🚀 NEW ACTIVATE trailing (PnL: {}%)", currentPnl);
            order.setTrailingActive(true);
            order.setPnlHigh(currentPnl);
            return false;
        }

        // Проверяем откат 30% от максимума (NEW_TRAILING_CLOSE_RATIO = 0.7, значит 30% откат)
        if (isTrailingActive && order.getPnlHigh() != null) {
            BigDecimal retrace = order.getPnlHigh().multiply(TRAILING_CLOSE_RETRACE_RATIO);
            if (currentPnl.compareTo(retrace) <= 0) {
                log.info("📉 NEW TRAILING RETRACE (high: {}%, current: {}%, retrace: {}%)",
                        order.getPnlHigh(),
                        currentPnl,
                        retrace);
                order.setTrailingActive(false);
                return true; // Нужно закрыть позицию
            }
        }

        return false;
    }

    // Старые методы удалены, так как не используются в новой логике

    /**
     * Выполняет закрытие позиции и обновляет сессию
     */
    private void executeClosePosition(TradeSession session, TradeOrder orderToClose, SessionMode sessionMode, String reason) {
        try {
            // Валидация входных параметров
            if (!isSessionInValidState(session)) {
                log.error("❌ Session validation failed for close position");
                return;
            }
            if (!isValidForClosing(orderToClose)) {
                log.error("❌ Order validation failed for close position: {}", orderToClose != null ? orderToClose.getOrderId() : "null");
                return;
            }

            // Cooldown: avoid spamming orders every monitor tick
            if (isInOrderCooldown(session.getId())) {
                log.warn("⏱️ Session {} → ORDER COOLDOWN active, skip close {}", session.getId(), orderToClose.getOrderId());
                return;
            }
            // Помечаем сессию как обрабатываемую
            session.setProcessing(true);

            log.info("🔧 Executing close position for session {}: orderId={}, direction={}, purpose={}, reason={}",
                    session.getId(), orderToClose.getOrderId(), orderToClose.getDirection(), orderToClose.getPurpose(), reason);

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
     * Выполняет закрытие двух позиций по очереди без ожидания (НОВЫЙ МЕТОД)
     * Этот метод готов к использованию, когда потребуется закрыть обе позиции одновременно
     */
    @SuppressWarnings("unused")
    private void executeTwoOrdersClose(TradeSession session, TradeOrder firstOrder, TradeOrder secondOrder, SessionMode sessionMode, String reason) {
        try {
            // Валидация входных параметров
            if (!isSessionInValidState(session)) {
                log.error("❌ Session validation failed for two orders close");
                return;
            }
            if (!isValidForClosing(firstOrder)) {
                log.error("❌ First order validation failed for two orders close: {}", firstOrder != null ? firstOrder.getOrderId() : "null");
                return;
            }
            if (!isValidForClosing(secondOrder)) {
                log.error("❌ Second order validation failed for two orders close: {}", secondOrder != null ? secondOrder.getOrderId() : "null");
                return;
            }

            // Cooldown: avoid spamming orders every monitor tick
            if (isInOrderCooldown(session.getId())) {
                log.warn("⏱️ Session {} → ORDER COOLDOWN active, skip two orders close", session.getId());
                return;
            }

            // Помечаем сессию как обрабатываемую
            session.setProcessing(true);

            log.info("🔧 Executing TWO ORDERS close for session {}: firstOrder={}, secondOrder={}, reason={}",
                    session.getId(), firstOrder.getOrderId(), secondOrder.getOrderId(), reason);

            BigDecimal currentPrice = ticker24hService.getPrice(session.getTradePlan());

            // Определяем правильные OrderPurpose для каждого ордера
            OrderPurpose firstOrderPurpose = determineCloseOrderPurpose(firstOrder);
            OrderPurpose secondOrderPurpose = determineCloseOrderPurpose(secondOrder);

            // Ставим метку кулдауна немедленно, чтобы не заспамить при сбое
            markOrderSent(session.getId());

            // Выполняем закрытие ПЕРВОГО ордера БЕЗ ОЖИДАНИЯ
            log.info("📤 Sending close request for FIRST order: {} ({})", firstOrder.getOrderId(), firstOrderPurpose);
            tradingUpdatesService.closePosition(
                    session, sessionMode, firstOrder.getOrderId(),
                    firstOrder.getRelatedHedgeId(), firstOrder.getDirection(),
                    firstOrderPurpose, currentPrice, reason + "_first"
            );

            // Выполняем закрытие ВТОРОГО ордера БЕЗ ОЖИДАНИЯ
            log.info("📤 Sending close request for SECOND order: {} ({})", secondOrder.getOrderId(), secondOrderPurpose);
            tradingUpdatesService.closePosition(
                    session, sessionMode, secondOrder.getOrderId(),
                    secondOrder.getRelatedHedgeId(), secondOrder.getDirection(),
                    secondOrderPurpose, currentPrice, reason + "_second"
            );

            log.info("✅ TWO close requests sent successfully for session {}", session.getId());

        } catch (Exception e) {
            log.error("❌ Error executing two orders close for session {}: {}", session.getId(), e.getMessage(), e);
            // Снимаем флаг обработки в случае ошибки
            session.setProcessing(false);
        }
    }

    /**
     * Определяет правильный OrderPurpose для закрытия ордера
     */
    private OrderPurpose determineCloseOrderPurpose(TradeOrder order) {
        if (order.getPurpose() == OrderPurpose.MAIN_OPEN) {
            return OrderPurpose.MAIN_CLOSE;
        } else if (order.getPurpose() == OrderPurpose.HEDGE_OPEN) {
            return OrderPurpose.HEDGE_CLOSE;
        } else {
            // По умолчанию используем HEDGE_CLOSE для безопасности
            log.warn("⚠️ Unknown order purpose for order {}, using HEDGE_CLOSE", order.getOrderId());
            return OrderPurpose.HEDGE_CLOSE;
        }
    }

    // === ВАЛИДАЦИОННЫЕ МЕТОДЫ ДЛЯ БЕЗОПАСНОСТИ ===

    /**
     * Проверяет валидность ордера для операций
     */
    private boolean isValidOrder(TradeOrder order) {
        if (order == null) {
            log.warn("⚠️ Order is null");
            return false;
        }
        if (order.getOrderId() == null) {
            log.warn("⚠️ Order {} has null orderId", order);
            return false;
        }
        if (order.getDirection() == null) {
            log.warn("⚠️ Order {} has null direction", order.getOrderId());
            return false;
        }
        if (order.getPurpose() == null) {
            log.warn("⚠️ Order {} has null purpose", order.getOrderId());
            return false;
        }
        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Order {} has invalid price: {}", order.getOrderId(), order.getPrice());
            return false;
        }
        return true;
    }

    /**
     * Проверяет совместимость направления с состоянием сессии
     */
    private boolean isDirectionCompatible(TradeSession session, TradingDirection direction) {
        if (direction == TradingDirection.LONG) {
            if (session.isActiveLong()) {
                log.warn("⚠️ Session {} already has active LONG position", session.getId());
                return false;
            }
        } else if (direction == TradingDirection.SHORT) {
            if (session.isActiveShort()) {
                log.warn("⚠️ Session {} already has active SHORT position", session.getId());
                return false;
            }
        }
        return true;
    }

    /**
     * Проверяет валидность ордера для закрытия
     */
    private boolean isValidForClosing(TradeOrder order) {
        if (!isValidOrder(order)) {
            return false;
        }

        // Проверяем, что это открывающий ордер
        if (order.getPurpose() != OrderPurpose.MAIN_OPEN && order.getPurpose() != OrderPurpose.HEDGE_OPEN) {
            log.warn("⚠️ Order {} is not an opening order, purpose: {}", order.getOrderId(), order.getPurpose());
            return false;
        }

        return true;
    }

    /**
     * Проверяет состояние сессии перед операциями
     */
    private boolean isSessionInValidState(TradeSession session) {
        if (session == null) {
            log.warn("⚠️ Session is null");
            return false;
        }
        if (session.getId() == null) {
            log.warn("⚠️ Session has null ID");
            return false;
        }
        if (session.getTradePlan() == null) {
            log.warn("⚠️ Session {} has null trade plan", session.getId());
            return false;
        }
        return true;
    }

    /**
     * Выполняет открытие хеджа и обновляет сессию
     */
    private void executeOpenHedge(TradeSession session, TradingDirection hedgeDirection, String purpose, BigDecimal currentPrice, String reason) {
        try {
            // ЗАЩИТА 0: Проверяем что не пытаемся открыть третью позицию
            if (session.hasBothPositionsActive()) {
                log.warn("🚫 Session {} → HEDGE EXECUTION BLOCKED: both positions already active", session.getId());
                return;
            }

            // Валидация входных параметров
            if (!isSessionInValidState(session)) {
                log.error("❌ Session validation failed for open hedge");
                return;
            }
            if (hedgeDirection == null) {
                log.error("❌ Hedge direction is null for session {}", session.getId());
                return;
            }
            if (!isDirectionCompatible(session, hedgeDirection)) {
                log.error("❌ Hedge direction {} incompatible with session {} state", hedgeDirection, session.getId());
                return;
            }

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
                followUpBySession.remove(updatedSession.getId());
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

        log.debug("🔍 getActiveOrderForMonitoring: session={}, activeLong={}, activeShort={}, direction={}",
                session.getId(), activeLong, activeShort, session.getDirection());

        if (!activeLong && !activeShort) {
            log.debug("🔍 No active positions found");
            return null;
        }

        // Если активна только LONG позиция — мониторим последний активный LONG (MAIN_OPEN или HEDGE_OPEN)
        if (activeLong && !activeShort) {
            TradeOrder longActive = getLatestActiveOrderByDirection(session, TradingDirection.LONG);
            log.debug("🔍 Returning latest active LONG order: {}", longActive != null ? longActive.getOrderId() : "null");
            return longActive;
        }

        // Если активна только SHORT позиция — мониторим последний активный SHORT (MAIN_OPEN или HEDGE_OPEN)
        if (activeShort && !activeLong) {
            TradeOrder shortActive = getLatestActiveOrderByDirection(session, TradingDirection.SHORT);
            log.debug("🔍 Returning latest active SHORT order: {}", shortActive != null ? shortActive.getOrderId() : "null");
            return shortActive;
        }

        // Если активны обе — для мониторинга вернем основной
        TradeOrder mainOrder = session.getMainOrder();
        log.debug("🔍 Returning main order for both active: {}", mainOrder != null ? mainOrder.getOrderId() : "null");
        return mainOrder;
    }

    private TradeOrder getLastFilledHedgeOrderByDirection(TradeSession session, TradingDirection direction) {
        return session.getOrders().stream()
                .filter(o -> OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .filter(o -> direction.equals(o.getDirection()))
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);
    }

    // Метод hasActivePositions удален, так как не используется в новой логике

    /**
     * Логика для одной позиции (НОВАЯ УПРОЩЕННАЯ ЛОГИКА)
     */
    private void applySinglePositionLogic(TradeSession session, BigDecimal price, TradeOrder activeOrder, BigDecimal pnl) {
        // Сценарий сопровождения убыточной после закрытия прибыльной
        FollowUpState followUp = followUpBySession.get(session.getId());
        if (followUp != null) {
            // Работать строго с убыточной ногой
            TradeOrder losingOrder = getLatestActiveOrderByDirection(session, followUp.getLosingDirection());
            if (losingOrder == null || losingOrder.getPrice() == null || losingOrder.getPrice().signum() == 0) {
                log.error("❌ FOLLOW-UP: cannot find losing order {}", followUp.getLosingDirection());
                followUpBySession.remove(session.getId());
                return;
            }

            BigDecimal entry = losingOrder.getPrice();
            BigDecimal losingPnl = (followUp.getLosingDirection() == TradingDirection.LONG)
                    ? price.subtract(entry).divide(entry, 8, RoundingMode.HALF_UP).multiply(PERCENTAGE_MULTIPLIER)
                    : entry.subtract(price).divide(entry, 8, RoundingMode.HALF_UP).multiply(PERCENTAGE_MULTIPLIER);

            // Фиксируем baseline именно для убыточной ноги
            if (!followUp.isProfitableClosed()) {
                followUp.setProfitableClosed(true);
                followUp.setBaselinePnlAtStart(losingPnl);
                log.info("🧭 Session {} → FOLLOW-UP START: baseline={}%, losingDir={}",
                        session.getId(), losingPnl, followUp.getLosingDirection());
            }

            BigDecimal deltaFromBaseline = losingPnl.subtract(followUp.getBaselinePnlAtStart());

            // Ухудшение: открыть хедж в противоположную losing стороне (если второй ноги нет)
            if (deltaFromBaseline.compareTo(FOLLOW_UP_WORSEN_DELTA_PCT) <= 0) {
                TradingDirection hedgeDirection = getOppositeDirection(losingOrder.getDirection());
                String context = String.format("follow_up_worsen delta<=%.3f from %.3f", FOLLOW_UP_WORSEN_DELTA_PCT, followUp.getBaselinePnlAtStart());
                log.info("🛡️ Session {} → FOLLOW-UP HEDGE (worsen by {}%)", session.getId(), deltaFromBaseline);
                executeOpenHedge(session, hedgeDirection, "HEDGE_OPEN", price, context);
                followUpBySession.remove(session.getId());
                return;
            }

            // Улучшение: включаем трейлинг на losing и открываем хедж (если второй ноги нет)
            if (deltaFromBaseline.compareTo(FOLLOW_UP_IMPROVE_DELTA_PCT) >= 0) {
                if (losingOrder.getTrailingActive() == null || !losingOrder.getTrailingActive()) {
                    losingOrder.setTrailingActive(true);
                    losingOrder.setPnlHigh(losingPnl);
                    log.info("🚀 Session {} → FOLLOW-UP: enable trailing for improving losing (pnl={}%)", session.getId(), losingPnl);
                }
                TradingDirection hedgeDirection = getOppositeDirection(losingOrder.getDirection());
                String context = String.format("follow_up_improve delta>=%.3f from %.3f", FOLLOW_UP_IMPROVE_DELTA_PCT, followUp.getBaselinePnlAtStart());
                executeOpenHedge(session, hedgeDirection, "HEDGE_OPEN", price, context);
                followUpBySession.remove(session.getId());
                return;
            }

            // 1/3 прибыли пары: закрыть убыточную, когда она улучшилась и достигла +1/3 ref
            BigDecimal ref = Optional.ofNullable(followUp.getProfitReferencePnl()).orElse(BigDecimal.ZERO);
            if (ref.signum() > 0) {
                BigDecimal target = ref.multiply(FOLLOW_UP_ONE_THIRD_PROFIT_RATIO);
                if (losingPnl.compareTo(target) >= 0) {
                    String context = String.format("follow_up_one_third target>=%.3f of ref=%.3f", target, ref);
                    executeClosePosition(session, losingOrder, SessionMode.SCALPING, context);
                    followUpBySession.remove(session.getId());
                    return;
                }
            }
            // Продолжаем мониторить по follow-up без стандартных правил
            return;
        }

        // === НОВЫЙ ТРЕЙЛИНГ (активация, затем закрытие по откату) ===
        if (checkNewTrailing(activeOrder, pnl)) {
            log.info("📉 Session {} → CLOSE POSITION (NEW TRAILING)", session.getId());
            String context = String.format("new_monitoring_trailing pnl>=%.3f retrace<=%.3f",
                    activeOrder.getPnlHigh(), activeOrder.getPnlHigh().multiply(TRAILING_CLOSE_RETRACE_RATIO));
            executeClosePosition(session, activeOrder, SessionMode.SCALPING, context);
            return;
        }

        // === ХЕДЖ ПРИ УБЫТКЕ ===
        if (pnl.compareTo(SINGLE_POSITION_HEDGE_THRESHOLD_PCT) <= 0) {
            // ЗАЩИТА 1: Проверяем, есть ли уже две активные позиции
            if (session.hasBothPositionsActive()) {
                log.debug("🚫 Session {} → HEDGE BLOCKED: both positions already active (LONG={}, SHORT={})",
                        session.getId(), session.isActiveLong(), session.isActiveShort());
                return;
            }

            TradingDirection hedgeDirection = getOppositeDirection(activeOrder.getDirection());

            // ЗАЩИТА 2: Проверяем, что направление хеджа не активно
            if ((hedgeDirection == TradingDirection.LONG && session.isActiveLong()) ||
                    (hedgeDirection == TradingDirection.SHORT && session.isActiveShort())) {
                log.debug("🚫 Session {} → HEDGE BLOCKED: target direction {} already active",
                        session.getId(), hedgeDirection);
                return;
            }

            String context = String.format("new_hedge_loss pnl<=%.3f", pnl);
            log.info("🛡️ Session {} → OPEN HEDGE (SINGLE POSITION loss at {}%)", session.getId(), pnl);
            executeOpenHedge(session, hedgeDirection, "HEDGE_OPEN", price, context);
            return;
        }
    }

    /**
     * Логика для двух позиций (НОВАЯ ЛОГИКА)
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

        // Определяем прибыльную и убыточную позиции
        boolean longIsProfitable = pnlLong.compareTo(pnlShort) > 0;
        String profitableDirection = longIsProfitable ? "LONG" : "SHORT";
        String losingDirection = longIsProfitable ? "SHORT" : "LONG";
        BigDecimal profitablePnl = longIsProfitable ? pnlLong : pnlShort;
        BigDecimal losingPnl = longIsProfitable ? pnlShort : pnlLong;
        TradeOrder profitableOrder = longIsProfitable ? longOrder : shortOrder;
        TradeOrder losingOrder = longIsProfitable ? shortOrder : longOrder;

        log.debug("💰 Session {}: PROFITABLE={} {}% | LOSING={} {}%",
                session.getId(), profitableDirection, profitablePnl,
                losingDirection, losingPnl);

        // Если прибыльная достигла порога — начинаем трейлить прибыльную и сразу стартуем сопровождение убыточной
        if (profitablePnl.compareTo(TWO_POS_PROFITABLE_ACTIVATION_PCT) >= 0) {
            // Инициируем/обновим follow-up для убыточной
            FollowUpState fu = followUpBySession.computeIfAbsent(session.getId(), k -> new FollowUpState());
            fu.setLosingDirection(losingOrder.getDirection());
            // Опорная прибыль = максимум из текущего хайя прибыльной и текущего профита
            BigDecimal profitableHigh = Optional.ofNullable(profitableOrder.getPnlHigh()).orElse(BigDecimal.ZERO);
            BigDecimal refCandidate = profitableHigh.max(profitablePnl);
            BigDecimal existingRef = Optional.ofNullable(fu.getProfitReferencePnl()).orElse(BigDecimal.ZERO);
            fu.setProfitReferencePnl(refCandidate.max(existingRef));
            // время старта сопровождения можно добавить при необходимости

            // Включаем трейлинг прибыльной; закрытие по откату
            if (checkNewTrailing(profitableOrder, profitablePnl)) {
                log.info("📉 Session {} → CLOSE {} (TRAILING)", session.getId(), profitableDirection);
                String context = String.format("two_pos_profitable_trailing pnl>=%.3f retrace<=%.3f",
                        profitableOrder.getPnlHigh(), profitableOrder.getPnlHigh().multiply(TRAILING_CLOSE_RETRACE_RATIO));
                executeClosePosition(session, profitableOrder, SessionMode.HEDGING, context);
                // Не удаляем follow-up — дальше будет одинарная позиция (убыточная)
            }
            return;
        }

        // Если порог не достигнут — ничего не делаем, продолжаем мониторинг
    }

    private TradingDirection getOppositeDirection(TradingDirection direction) {
        return direction == TradingDirection.LONG ? TradingDirection.SHORT : TradingDirection.LONG;
    }

    private TradeOrder getLatestActiveOrderByDirection(TradeSession session, TradingDirection direction) {
        // Находим последний FILLED открывающий ордер нужного направления, который не закрыт
        List<TradeOrder> allOrders = session.getOrders().stream()
                .filter(o -> OrderStatus.FILLED.equals(o.getStatus()))
                .filter(o -> direction.equals(o.getDirection()))
                .filter(o -> OrderPurpose.MAIN_OPEN.equals(o.getPurpose()) || OrderPurpose.HEDGE_OPEN.equals(o.getPurpose()))
                .collect(Collectors.toList());

        log.debug("🔍 getLatestActiveOrderByDirection: session={}, direction={}, found {} orders",
                session.getId(), direction, allOrders.size());

        List<TradeOrder> activeOrders = allOrders.stream()
                .filter(o -> !isOpenOrderClosed(session, o))
                .collect(Collectors.toList());

        log.debug("🔍 getLatestActiveOrderByDirection: session={}, direction={}, {} orders are active",
                session.getId(), direction, activeOrders.size());

        TradeOrder result = activeOrders.stream()
                .max(Comparator.comparing(TradeOrder::getOrderTime))
                .orElse(null);

        log.debug("🔍 getLatestActiveOrderByDirection: session={}, direction={}, result={}",
                session.getId(), direction, result != null ? result.getOrderId() : "null");

        return result;
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
        return false;
    }
}
package io.cryptobot.binance.trading.monitoring;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.session.service.TradeSessionService;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import io.cryptobot.binance.order.enums.OrderStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringServiceImpl implements MonitoringService {
    private final TradeSessionService sessionService;
    private final Ticker24hService ticker24hService;

    private Map<String, TradeSession> sessions = new HashMap<>();

    @PostConstruct
    public void init() {
        // Создаем основной ордер (SHORT)
        TradeOrder mainOrder = TradeOrder.builder()
                .orderId(123L)
                .direction(TradingDirection.SHORT)
                .creationContext("context")
                .purpose(OrderPurpose.MAIN_OPEN)
                .symbol("SUIUSDT")
                .side(OrderSide.SELL)
                .type("MARKET")
                .count(BigDecimal.valueOf(1))
                .price(BigDecimal.valueOf(3.469))
                .commission(BigDecimal.valueOf(1))
                .commissionAsset("USDT")
                .pnl(BigDecimal.ZERO)
                .leverage(10)
                .modeAtCreation(SessionMode.SCALPING)
                .orderTime(LocalDateTime.now().minusHours(1))
                .trailingActive(false)
                .pnlHigh(BigDecimal.ZERO)
                .basePnl(null)
                .maxChangePnl(null)
                .status(OrderStatus.FILLED)
                .amount(BigDecimal.ZERO)
                .parentOrderId(null)
                .relatedHedgeId(null)
                .build();

        // Создаем хедж ордер (LONG)
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(124L)
                .direction(TradingDirection.LONG)
                .creationContext("hedge_context")
                .purpose(OrderPurpose.HEDGE_OPEN)
                .symbol("SUIUSDT")
                .side(OrderSide.BUY)
                .type("MARKET")
                .count(BigDecimal.valueOf(1))
                .price(BigDecimal.valueOf(3.484))
                .commission(BigDecimal.valueOf(1))
                .commissionAsset("USDT")
                .pnl(BigDecimal.ZERO)
                .leverage(10)
                .modeAtCreation(SessionMode.HEDGING)
                .orderTime(LocalDateTime.now().minusMinutes(30))
                .trailingActive(false)
                .pnlHigh(BigDecimal.ZERO)
                .basePnl(null)
                .maxChangePnl(null)
                .status(OrderStatus.FILLED)
                .amount(BigDecimal.ZERO)
                .parentOrderId("123") // ссылка на основной ордер
                .relatedHedgeId(null)
                .build();

        // Создаем сессию с основным ордером
        TradeSession session = new TradeSession();
        session.onCreate("SUIUSDT", TradingDirection.SHORT, mainOrder, "context");
        session.setId("123ID");
        
        // Добавляем хедж ордер в сессию
        session.addOrder(hedgeOrder);
        
        // Переключаем в режим хеджирования
        session.changeMode(SessionMode.HEDGING);

        List<TradeSession> ses = new ArrayList<>();
        ses.add(session);
//        List<TradeSession> ses = sessionService.getAllActive();
        for (TradeSession tradeSession : ses) {
            sessions.put(tradeSession.getId(), tradeSession);
        }
        
        log.info("🧪 Test session created: MAIN(SHORT @ 3.483) + HEDGE(LONG @ 3.484)");
    }

    @Override
    public void addToMonitoring(TradeSession tradeSession) {
        sessions.put(tradeSession.getId(), tradeSession);
    }

    @Scheduled(fixedRate = 10_000)
    public void monitor() {
//        List<TradeSession> activeSessions = (List<TradeSession>) sessions.values(); //todo
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
        
        // 1. получаем цену
        BigDecimal price = ticker24hService.getPrice(session.getTradePlan());
        if (price == null) {
            log.warn("⚠️ Session {}: Failed to get price, skipping monitoring", session.getId());
            return;
        }
        log.debug("💰 Session {}: Current price = {}", session.getId(), price);

        // 2. основной ордер
        TradeOrder main = session.getMainOrder();
        if (main == null || main.getPrice() == null) {
            log.warn("⚠️ Session {}: Main order is null or has no price, skipping", session.getId());
            return;
        }
        BigDecimal entryMain = main.getPrice();
        log.debug("📊 Session {}: Main order entry price = {}", session.getId(), entryMain);

        // 3. PnL для main в долях
        BigDecimal pnlMain = price.subtract(entryMain)
                .divide(entryMain, 8, RoundingMode.HALF_UP)
                .multiply(session.getDirection() == TradingDirection.LONG
                        ? BigDecimal.ONE
                        : BigDecimal.valueOf(-1));
        log.info("📈 Session {} MAIN PnL={}% (price: {}, entry: {})", 
                session.getId(), pnlMain.multiply(BigDecimal.valueOf(100)), price, entryMain);

        // === РЕЖИМ ОДНОЙ ПОЗИЦИИ (СКАЛЬПИНГ) ===
        if (session.isInScalpingMode()) {
            log.debug("🎯 Session {}: In SCALPING mode", session.getId());
            
            // === ТРЕЙЛИНГ ===
            if (checkTrailing(main, pnlMain)) {
                log.info("📉 Session {} → CLOSE MAIN (TRAILING)", session.getId());
                // TODO: послать команду на закрытие main
                return;
            }

            // === ОТСЛЕЖИВАНИЕ включается только если PnL <= -0.3% ===
            if (pnlMain.compareTo(BigDecimal.valueOf(-0.003)) > 0) {
                // Сбрасываем tracking если PnL > -0.3%
                main.setBasePnl(null);
                main.setMaxChangePnl(null);
                log.debug("🔄 Session {}: Reset tracking (PnL > -0.3%)", session.getId());
            } else {
                // === Если tracking ещё не активен — запускаем ===
                if (main.getBasePnl() == null) {
                    startTracking(main, pnlMain);
                    log.info("�� Session {} → START TRACKING at {}%", 
                            session.getId(), pnlMain.multiply(BigDecimal.valueOf(100)));
                }

                BigDecimal delta = pnlMain.subtract(main.getBasePnl());

                // ✅ Ухудшение -0.1% → открываем хедж
                if (delta.compareTo(BigDecimal.valueOf(-0.001)) <= 0) {
                    log.info("🛡️ Session {} → OPEN HEDGE (worsening {}% from {}%)", 
                            session.getId(), delta.multiply(BigDecimal.valueOf(100)), 
                            main.getBasePnl().multiply(BigDecimal.valueOf(100)));
                    // TODO: послать команду на открытие хеджа
                    session.changeMode(SessionMode.HEDGING);
                    log.info("🔄 Session {} → Switched to HEDGING mode", session.getId());
                    return;
                }

                // ✅ Фиксируем максимум улучшения
                if (delta.compareTo(main.getMaxChangePnl() != null ? main.getMaxChangePnl() : BigDecimal.ZERO) > 0) {
                    main.setMaxChangePnl(delta);
                    log.debug("📈 Session {}: New max improvement: {}%", 
                            session.getId(), delta.multiply(BigDecimal.valueOf(100)));
                }

                // ✅ Улучшение > +0.1% и откат ≥30% → хедж
                BigDecimal maxImp = main.getMaxChangePnl() != null ? main.getMaxChangePnl() : BigDecimal.ZERO;
                if (maxImp.compareTo(BigDecimal.valueOf(0.001)) > 0 && 
                    delta.compareTo(maxImp.multiply(BigDecimal.valueOf(0.7))) <= 0) {
                    log.info("🛡️ Session {} → OPEN HEDGE (improvement {}%, pullback ≥30%)", 
                            session.getId(), maxImp.multiply(BigDecimal.valueOf(100)));
                    // TODO: послать команду на открытие хеджа
                    session.changeMode(SessionMode.HEDGING);
                    log.info("🔄 Session {} → Switched to HEDGING mode", session.getId());
                    return;
                }
            }
        }

        // === РЕЖИМ ДВУХ ПОЗИЦИЙ (ХЕДЖ) ===
        if (session.isInHedgeMode()) {
            log.debug("🛡️ Session {}: In HEDGING mode", session.getId());
            
            TradeOrder hedge = session.getLastHedgeOrder();
            if (hedge == null || hedge.getPrice() == null) {
                log.warn("⚠️ Session {}: Hedge order is null or has no price, skipping hedge monitoring", session.getId());
                return;
            }
            BigDecimal entryH = hedge.getPrice();
            log.debug("📊 Session {}: Hedge order entry price = {}", session.getId(), entryH);

            // PnL для hedge
            BigDecimal pnlHedge = session.getDirection() == TradingDirection.LONG
                    ? entryH.subtract(price)
                    .divide(entryH, 8, RoundingMode.HALF_UP)
                    : price.subtract(entryH)
                    .divide(entryH, 8, RoundingMode.HALF_UP);
            log.info("📈 Session {} HEDGE PnL={}% (price: {}, entry: {})", 
                    session.getId(), pnlHedge.multiply(BigDecimal.valueOf(100)), price, entryH);

            // Определяем лучшую и худшую позиции
            String bestDirection = pnlMain.compareTo(pnlHedge) > 0 ? "MAIN" : "HEDGE";
            String worstDirection = pnlMain.compareTo(pnlHedge) > 0 ? "HEDGE" : "MAIN";
            BigDecimal bestPnl = pnlMain.compareTo(pnlHedge) > 0 ? pnlMain : pnlHedge;
            BigDecimal worstPnl = pnlMain.compareTo(pnlHedge) > 0 ? pnlHedge : pnlMain;
            TradeOrder bestOrder = pnlMain.compareTo(pnlHedge) > 0 ? main : hedge;
            TradeOrder worstOrder = pnlMain.compareTo(pnlHedge) > 0 ? hedge : main;

            log.info("🏆 Session {}: BEST={} {}% | WORST={} {}%", 
                    session.getId(), bestDirection, bestPnl.multiply(BigDecimal.valueOf(100)), 
                    worstDirection, worstPnl.multiply(BigDecimal.valueOf(100)));

            // === ТРЕЙЛИНГ для лучшей позиции ===
            if (checkTrailing(bestOrder, bestPnl)) {
                log.info("📉 Session {} → CLOSE {} (TRAILING BEST)", session.getId(), bestDirection);
                // TODO: послать команду на закрытие лучшей позиции
                startTracking(worstOrder, worstPnl); // отслеживание worst запускаем только после закрытия best
                log.info("📊 Session {} → START TRACKING WORST at {}%", 
                        session.getId(), worstPnl.multiply(BigDecimal.valueOf(100)));
                return;
            }

            // === Если worst снова в зоне отслеживания ===
            if (worstPnl.compareTo(BigDecimal.valueOf(-0.003)) <= 0 && worstOrder.getBasePnl() == null) {
                startTracking(worstOrder, worstPnl);
                log.info("📊 Session {} → START TRACKING WORST at {}%", 
                        session.getId(), worstPnl.multiply(BigDecimal.valueOf(100)));
            }

            // Если tracking не активен — выходим
            if (worstOrder.getBasePnl() == null) {
                return;
            }

            BigDecimal delta = worstPnl.subtract(worstOrder.getBasePnl());

            // ✅ Ухудшение WORST на -0.1% → открываем хедж
            if (delta.compareTo(BigDecimal.valueOf(-0.001)) <= 0) {
                log.info("🛡️ Session {} → RE-HEDGE (worsening WORST {}% from {}%)", 
                        session.getId(), delta.multiply(BigDecimal.valueOf(100)), 
                        worstOrder.getBasePnl().multiply(BigDecimal.valueOf(100)));
                // TODO: послать команду на новый hedge
                return;
            }

            // ✅ Фиксируем максимум улучшения
            if (delta.compareTo(worstOrder.getMaxChangePnl() != null ? worstOrder.getMaxChangePnl() : BigDecimal.ZERO) > 0) {
                worstOrder.setMaxChangePnl(delta);
                log.debug("📈 Session {}: New worst max improvement: {}%", 
                        session.getId(), delta.multiply(BigDecimal.valueOf(100)));
            }

            // ✅ Улучшение > +0.1% и откат ≥30% → открываем хедж
            BigDecimal maxImp = worstOrder.getMaxChangePnl() != null ? worstOrder.getMaxChangePnl() : BigDecimal.ZERO;
            if (maxImp.compareTo(BigDecimal.valueOf(0.001)) > 0 && 
                delta.compareTo(maxImp.multiply(BigDecimal.valueOf(0.7))) <= 0) {
                log.info("🛡️ Session {} → RE-HEDGE (improvement WORST {}%, pullback ≥30%)", 
                        session.getId(), maxImp.multiply(BigDecimal.valueOf(100)));
                // TODO: послать команду на новый hedge
                return;
            }
        }
        
        log.debug("✅ Session {}: Monitoring cycle completed successfully", session.getId());
    }

    /**
     * Проверяет трейлинг для позиции
     */
    private boolean checkTrailing(TradeOrder order, BigDecimal currentPnl) {
        if (currentPnl.compareTo(order.getPnlHigh() != null ? order.getPnlHigh() : BigDecimal.ZERO) > 0) {
            order.setPnlHigh(currentPnl);
            log.debug("📈 New high reached: {}%", currentPnl.multiply(BigDecimal.valueOf(100)));
        }
        
        // Трейлинг активируется при PnL >= 0.15%
        if (currentPnl.compareTo(BigDecimal.valueOf(0.0015)) >= 0 && !order.getTrailingActive()) {
            log.info("🚀 ACTIVATE trailing (PnL: {}%)", currentPnl.multiply(BigDecimal.valueOf(100)));
            order.setTrailingActive(true);
            order.setPnlHigh(currentPnl);
            return false;
        }
        
        // Проверяем откат 20% от максимума
        if (order.getTrailingActive() && order.getPnlHigh() != null) {
            BigDecimal retrace = order.getPnlHigh().multiply(BigDecimal.valueOf(0.8));
            if (currentPnl.compareTo(retrace) <= 0) {
                log.info("📉 TRAILING RETRACE (high: {}%, current: {}%, retrace: {}%)", 
                        order.getPnlHigh().multiply(BigDecimal.valueOf(100)), 
                        currentPnl.multiply(BigDecimal.valueOf(100)), 
                        retrace.multiply(BigDecimal.valueOf(100)));
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
        log.info("📊 START TRACKING at {}%", basePnl.multiply(BigDecimal.valueOf(100)));
    }

    //monitoring pnl
    //check tryling
    //open close hedge
}

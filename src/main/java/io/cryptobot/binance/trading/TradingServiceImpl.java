package io.cryptobot.binance.trading;

import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.market_data.aggTrade.AggTrade;
import io.cryptobot.market_data.aggTrade.AggTradeService;
import io.cryptobot.market_data.depth.DepthModel;
import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.utils.logging.TradingLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingServiceImpl implements TradingService {

//    private static final int AGG_TRADE_LIMIT = 3600;  // управляется на стороне сервиса тиков
    private static final double DEPTH_EPS = 1e-6;

    private final TradePlanGetService tradePlanGetService;
    private final AggTradeService aggTradeService;
    private final DepthService depthService;
    private final TradingLogWriter logWriter;
//    private final TradingProcessService tradingProcessService;

    private final Map<String, Direction> lastDecisionMap = new ConcurrentHashMap<>();
    private final Map<String, Integer>   streakMap      = new ConcurrentHashMap<>();
    private final Map<String, Long>      lockMap        = new ConcurrentHashMap<>();

    private final ExecutorService executor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    @Scheduled(initialDelay = 10_000, fixedRate = 1_000)
    public void startDemo() {
        tradePlanGetService.getAllActiveFalse().stream()
                .filter(plan -> !plan.getClose())
                .forEach(plan -> executor.submit(() -> analyzeSymbol(plan)));
    }

    private void analyzeSymbol(TradePlan plan) {
        final String symbol = plan.getSymbol();

        // check if plan in lock
        Long lockTime = lockMap.get(symbol);
        if (lockTime != null && System.currentTimeMillis() - lockTime < 60_000) return;

        final TradeMetrics m = plan.getMetrics();
        final double EMA_SENS   = m.getEmaSensitivity();
        final int    DEPTH_LV   = m.getDepthLevels();
        final double VOL_TH     = m.getVolRatioThreshold();
        final double MIN_LP     = m.getMinLongPct();
        final double MIN_SP     = m.getMinShortPct();
        final double MIN_IMB_L  = m.getMinImbalanceLong();
        final double MAX_IMB_S  = m.getMaxImbalanceShort();
        final int    VOL_WIN    = m.getVolWindowSec();

        long t0 = System.currentTimeMillis();
        Direction finalDecision = Direction.NEUTRAL;

        try {
            // === Тики (Deque: head=newest, tail=oldest) ===
            Deque<AggTrade> ticks = aggTradeService.getRecentTradesDeque(symbol);
            if (ticks.isEmpty()) return;

            // === EMA по хронологии (old->new через descendingIterator) ===
            double ema20 = calculateEMA(ticks, 20);
            double ema50 = calculateEMA(ticks, 50);
            Direction emaDir = dirEma(ema20, ema50, EMA_SENS);

            // === Volume Ratio (baseline 60s, окно VOL_WIN) ===
            double volRatio = calcVolRatio(ticks, VOL_WIN);
            Direction volDir = dirVolume(volRatio, VOL_TH, emaDir); // объём — усилитель тренда

            // === Order Book Imbalance (top DEPTH_LV уровней) ===
            DepthModel depth = depthService.getDepthModelBySymbol(symbol);
            if (depth == null || depth.getBids().isEmpty() || depth.getAsks().isEmpty()) return;
            double imbalance = calcImbalance(depth, DEPTH_LV);
            Direction imbDir = dirImbalance(imbalance, MIN_IMB_L, MAX_IMB_S);

            // === Long/Short % по тикам ===
            double[] ls = calcLongShortPct(ticks);
            double lp = ls[0], sp = ls[1];
            Direction lsrDir = dirLongShort(lp, sp, MIN_LP, MIN_SP);

            // === Текущая цена = самый новый тик (head) ===
            double currentPrice = ticks.peekFirst() != null ? ticks.peekFirst().getPrice().doubleValue() : 0.0;

            // === Агрегатор: строго 4/4 в одну сторону ===
            Direction decision = aggregateStrict(emaDir, volDir, imbDir, lsrDir);

            if (decision != Direction.NEUTRAL) {
                IndicatorSnapshot snap = new IndicatorSnapshot(
                        emaDir, ema20, ema50,
                        volDir, volRatio,
                        imbDir, imbalance,
                        lsrDir, lp, sp,
                        currentPrice
                );
                writePrettyLog(symbol, snap, decision);
            }

            // === Требуется 3 подряд одинаковых решения ===
            Direction last = lastDecisionMap.getOrDefault(symbol, Direction.NEUTRAL);
            int streak = streakMap.getOrDefault(symbol, 0);

            if (decision == Direction.NEUTRAL) {
                last = Direction.NEUTRAL;
                streak = 0;
            } else if (decision == last) {
                streak++;
            } else {
                last = decision;
                streak = 1;
            }
            lastDecisionMap.put(symbol, last);
            streakMap.put(symbol, streak);

            if (decision != Direction.NEUTRAL && streak >= 3) {
                finalDecision = decision;
                logWriter.writeTradeLog(symbol, "📊 Final Decision: " + decision);
                streakMap.put(symbol, 0);

                sendSignalToProcessing(
                        plan,
                        decision.name(),
                        currentPrice,
                        emaDir.name(),
                        volRatio, imbalance, lp, sp
                );
            }

        } catch (Exception ex) {
            log.error("Error analysis {}: {}", symbol, ex.getMessage(), ex);
        } finally {
            long dt = System.currentTimeMillis() - t0;
            if (finalDecision != Direction.NEUTRAL) {
                logWriter.writeTradeLog(symbol, String.format("⏱ Analysis completed in %d ms", dt));
            }
        }
    }

    /* ===================== Indicators & Helpers ===================== */

    private Direction dirEma(double ema20, double ema50, double sens) {
        double up = ema50 * (1 + sens);
        double dn = ema50 * (1 - sens);
        if (ema20 > up) return Direction.LONG;
        if (ema20 < dn) return Direction.SHORT;
        return Direction.NEUTRAL;
    }

    private Direction dirVolume(double volRatio, double threshold, Direction trendDir) {
        // высокий объём поддерживает текущий тренд; без объёма — нейтрально
        if (volRatio >= threshold) {
            return trendDir;
        }
        return Direction.NEUTRAL;
    }

    private Direction dirImbalance(double imbalance, double minLong, double maxShort) {
        if (imbalance >= minLong) return Direction.LONG;
        if (imbalance <= maxShort) return Direction.SHORT;
        return Direction.NEUTRAL;
    }

    private Direction dirLongShort(double lp, double sp, double minLongPct, double minShortPct) {
        if (lp >= minLongPct) return Direction.LONG;
        if (sp >= minShortPct) return Direction.SHORT;
        return Direction.NEUTRAL;
    }

    private Direction aggregateStrict(Direction ema, Direction vol, Direction imb, Direction lsr) {
        boolean allLong  = ema == Direction.LONG  && vol == Direction.LONG  && imb == Direction.LONG  && lsr == Direction.LONG;
        boolean allShort = ema == Direction.SHORT && vol == Direction.SHORT && imb == Direction.SHORT && lsr == Direction.SHORT;
        if (allLong)  return Direction.LONG;
        if (allShort) return Direction.SHORT;
        return Direction.NEUTRAL;
    }

    private double[] calcLongShortPct(Deque<AggTrade> dq) {
        double lv = 0.0, sv = 0.0;
        for (AggTrade t : dq) {
            double q = t.getQuantity().doubleValue();
            if (!t.isBuyerIsMaker()) lv += q; else sv += q;
        }
        double tot = lv + sv;
        double lp = tot > 0 ? lv / tot * 100.0 : 0.0;
        double sp = tot > 0 ? sv / tot * 100.0 : 0.0;
        return new double[]{lp, sp};
    }

    private double calcImbalance(DepthModel depth, int levels) {
        double bids = depth.getBids().entrySet().stream()
                .limit(levels)
                .mapToDouble(e -> e.getValue().doubleValue())
                .sum();
        double asks = depth.getAsks().entrySet().stream()
                .limit(levels)
                .mapToDouble(e -> e.getValue().doubleValue())
                .sum();
        return bids / (bids + asks + DEPTH_EPS);
    }

    private double calcVolRatio(Deque<AggTrade> dq, int windowSec) {
        long nowSec = java.time.Instant.now().getEpochSecond();

        final int BASELINE_SEC = 60;
        long baseFrom = nowSec - BASELINE_SEC;
        long winFrom  = nowSec - windowSec;

        double vol60 = 0.0;
        double curVol = 0.0;

        for (AggTrade t : dq) {
            long ts = t.getTradeTime() / 1000; // мс → с
            double q = t.getQuantity().doubleValue();
            if (ts >= baseFrom && ts <= nowSec) vol60 += q;
            if (ts >= winFrom  && ts <= nowSec) curVol += q;
        }

        double avgPerSec = vol60 / BASELINE_SEC;
        if (avgPerSec <= 0.0) return 0.0;

        double baselineForWindow = avgPerSec * windowSec;
        return curVol / baselineForWindow;
    }

    /** EMA по хронологии: oldest->newest через descendingIterator() */
    private double calculateEMA(Deque<AggTrade> dq, int period) {
        if (dq.isEmpty()) return 0.0;

        var it = dq.descendingIterator(); // tail -> head (oldest -> newest)
        if (!it.hasNext()) return 0.0;

        int n = dq.size();
        if (n < period) {
            double sum = 0.0;
            while (it.hasNext()) sum += it.next().getPrice().doubleValue();
            return sum / n;
        }

        double k = 2.0 / (period + 1);
        double ema = it.next().getPrice().doubleValue(); // старт с самого старого
        while (it.hasNext()) {
            double p = it.next().getPrice().doubleValue();
            ema = p * k + ema * (1 - k);
        }
        return ema;
    }

    /* ===================== Logging & Processing ===================== */

    private void writePrettyLog(String symbol, IndicatorSnapshot s, Direction decision) {
        String msg =
                "\n────────────────────────────────────────────────────────\n" +
                        String.format("• %s | Price: %.6f\n", symbol, s.price()) +
                        String.format("  EMA_TREND   : %-8s (EMA20=%.6f / EMA50=%.6f)\n", s.emaDir(), s.ema20(), s.ema50()) +
                        String.format("  VOL_RATIO   : %-8s (%.2fx)\n", s.volDir(), s.volRatio()) +
                        String.format("  IMBALANCE   : %-8s (%.3f)\n", s.imbDir(), s.imbalance()) +
                        String.format("  LONG/SHORT  : %-8s (L=%.1f%% / S=%.1f%%)\n", s.lsrDir(), s.longPct(), s.shortPct()) +
                        String.format("➜ DECISION    : %s\n", decision) +
                        "────────────────────────────────────────────────────────";
        logWriter.writeTradeLog(symbol, msg);
    }

    private void sendSignalToProcessing(TradePlan plan, String signal, double currentPrice, String trend, double volRatio, double imbalance, double lp, double sp) {
        String symbol = plan.getSymbol();
        lockMap.put(symbol, System.currentTimeMillis());

        String context = String.format(
                "SIGNAL: %s | EMA: %s | VOL: %.2fx | IMB: %.3f | L/S: %.1f%%/%.1f%% | PRICE: %.6f",
                signal, trend, volRatio, imbalance, lp, sp, currentPrice
        );

        log.info("🚀 Sending signal to processing: {} | {}", symbol, context);
        try {
//            TradingDirection dir = TradingDirection.valueOf(signal);
            // tradingProcessService.openOrder(plan, dir, BigDecimal.valueOf(currentPrice), context); // todo open
            log.info("✅ Signal sent to processing for {}", symbol);
        } catch (Exception e) {
            log.error("❌ Error sending signal to processing for {}: {}", symbol, e.getMessage(), e);
            lockMap.remove(symbol);
        }
    }
}

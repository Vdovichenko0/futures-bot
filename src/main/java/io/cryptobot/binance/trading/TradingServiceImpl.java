package io.cryptobot.binance.trading;

import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.binance.trading.process.TradingProcessService;
import io.cryptobot.market_data.aggTrade.AggTrade;
import io.cryptobot.market_data.aggTrade.AggTradeService;
import io.cryptobot.market_data.depth.DepthModel;
import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.market_data.klines.enums.IntervalE;
import io.cryptobot.market_data.klines.model.KlineModel;
import io.cryptobot.market_data.klines.service.KlineService;
import io.cryptobot.calculator.Calculator;
import io.cryptobot.calculator.EmaValues;
import io.cryptobot.utils.logging.TradingLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingServiceImpl implements TradingService {
    private static final int AGG_TRADE_LIMIT = 3600;
    private static final double DEPTH_EPS = 1e-6;

    private final TradePlanGetService tradePlanGetService;
    private final KlineService klineService;
    private final AggTradeService aggTradeService;
    private final DepthService depthService;
    private final TradingLogWriter logWriter;
    private final TradingProcessService tradingProcessService;

    private final Map<String, String> lastSignalMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> sameCountMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lockMap = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    @Scheduled(initialDelay = 10_000, fixedRate = 1_000)
    public void startDemo() {
        tradePlanGetService.getAllActiveFalse().stream()
                .filter(plan -> !plan.getClose())
                .forEach(plan -> executor.submit(() -> analyzeSymbol(plan)));
    }

    //todo lock plan if get signal to 1 min
    private void analyzeSymbol(TradePlan plan) {
        String symbol = plan.getSymbol();
        
        // Проверяем блокировку на 1 минуту
        Long lockTime = lockMap.get(symbol);
        if (lockTime != null && System.currentTimeMillis() - lockTime < 60_000) {
            return; 
        }
        
        TradeMetrics metrics = plan.getMetrics();
        final double EMA_SENSITIVITY = metrics.getEmaSensitivity();
        final int DEPTH_LEVELS = metrics.getDepthLevels();
        final double VOL_RATIO_THRESHOLD = metrics.getVolRatioThreshold();
        final double MIN_LONG_PCT = metrics.getMinLongPct();
        final double MIN_SHORT_PCT = metrics.getMinShortPct();
        final double MIN_IMBALANCE_LONG = metrics.getMinImbalanceLong();
        final double MAX_IMBALANCE_SHORT = metrics.getMaxImbalanceShort();

        long startTime = System.currentTimeMillis();
        String signal = "NO"; 
        try {
            // 1. EMA Trend & diff
            List<KlineModel> klines = klineService.getKlines(symbol, IntervalE.ONE_MINUTE);
            if (klines == null || klines.isEmpty()) {
                log.warn("No klines available for symbol: {}", symbol);
                return;
            }
            EmaValues ema = Calculator.calculateEma(klines, false, true, true, false);
            double ema20 = ema.getEma20();
            double ema50 = ema.getEma50();
            String trend;
            if (ema20 > ema50 * (1 + EMA_SENSITIVITY)) {
                trend = "long";
            } else if (ema20 < ema50 * (1 - EMA_SENSITIVITY)) {
                trend = "short";
            } else {
                trend = null;
            }

            // 2. VolRatio
            List<AggTrade> ticks = aggTradeService.getRecentTrades(symbol, AGG_TRADE_LIMIT);
            if (ticks.isEmpty()) {
                return;
            }

            long nowSec = Instant.now().getEpochSecond();
            double totalMinuteVol = ticks.stream()
                    .filter(t -> nowSec - (t.getTradeTime() / 1_000) <= 60)
                    .mapToDouble(t -> t.getQuantity().doubleValue())
                    .sum();
            double avgSecVol = totalMinuteVol > 0 ? totalMinuteVol / 60.0 : 0.0;
            double currentSecVol = ticks.stream()
                    .filter(t -> nowSec - (t.getTradeTime() / 1_000) <= 1)
                    .mapToDouble(t -> t.getQuantity().doubleValue())
                    .sum();
            double volRatio = avgSecVol > 0 ? currentSecVol / avgSecVol : 0.0;

            // 3. Imbalance [0…1]
            DepthModel depth = depthService.getDepthModelBySymbol(symbol);
            if (depth == null || depth.getBids() == null || depth.getAsks() == null ||
                    depth.getBids().isEmpty() || depth.getAsks().isEmpty()) {
                log.warn("No depth data available for symbol: {}", symbol);
                return;
            }
            double bids = depth.getBids().entrySet().stream().limit(DEPTH_LEVELS)
                    .mapToDouble(e -> e.getValue().doubleValue()).sum();
            double asks = depth.getAsks().entrySet().stream().limit(DEPTH_LEVELS)
                    .mapToDouble(e -> e.getValue().doubleValue()).sum();
            double imbalance = bids / (bids + asks + DEPTH_EPS);

            // 4. Long/Short % (0…100)
            double lv = ticks.stream()
                    .filter(t -> !t.isBuyerIsMaker())
                    .mapToDouble(t -> t.getQuantity().doubleValue())
                    .sum();
            double sv = ticks.stream()
                    .filter(AggTrade::isBuyerIsMaker)
                    .mapToDouble(t -> t.getQuantity().doubleValue())
                    .sum();
            double total = lv + sv;
            double lp = total > 0 ? lv / total * 100.0 : 0.0;
            double sp = total > 0 ? sv / total * 100.0 : 0.0;

            // 5. Signal conditions (точно как в Python)
            boolean volumeOk = volRatio >= VOL_RATIO_THRESHOLD;
            boolean longOk = "long".equals(trend)
                    && volumeOk
                    && lp > MIN_LONG_PCT
                    && imbalance > MIN_IMBALANCE_LONG;
            boolean shortOk = "short".equals(trend)
                    && volumeOk
                    && sp > MIN_SHORT_PCT
                    && imbalance < MAX_IMBALANCE_SHORT;
            
            TradingDirection signalDirection = longOk ? TradingDirection.LONG : shortOk ? TradingDirection.SHORT : null;
            signal = signalDirection != null ? signalDirection.name() : "NO";

            String last = lastSignalMap.getOrDefault(symbol, "NO");
            int count = sameCountMap.getOrDefault(symbol, 0);
            if ("NO".equals(signal)) {
                last = "NO";
                count = 0;
            } else if (signal.equals(last)) {
                count++;
            } else {
                last = signal;
                count = 1;
            }
            lastSignalMap.put(symbol, last);
            sameCountMap.put(symbol, count);

            // Получаем текущую цену из последнего тика
            double currentPrice = ticks.isEmpty() ? 0.0 : ticks.get(0).getPrice().doubleValue();

            // Логируем только если сигнал не NO
            if (!"NO".equals(signal)) {
                String logMessage = String.format("Price: %.6f | EMA: %s(%.6f/%.6f) | Volume: %s(%.2f) | Imbalance: %s(%.3f) | Long/Short: %s(%.1f%%/%.1f%%) | Signal: %s | Count: %d",
                        currentPrice,
                        trend != null ? trend.toUpperCase() : "NEUTRAL", ema20, ema50,
                        volumeOk ? "HIGH" : "LOW", volRatio,
                        imbalance > MIN_IMBALANCE_LONG ? "LONG" : imbalance < MAX_IMBALANCE_SHORT ? "SHORT" : "NEUTRAL", imbalance,
                        lp > MIN_LONG_PCT ? "LONG" : sp > MIN_SHORT_PCT ? "SHORT" : "NEUTRAL", lp, sp,
                        signal, count);

                logWriter.writeTradeLog(symbol, logMessage);
            }

            // 7. Final logging on 3 подряд сигнала для всех индикаторов
            if (count >= 3) {
                logWriter.writeTradeLog(symbol, "📊 Final Signal: " + signal);
                logWriter.writeTradeLog(symbol, "==============new cycle==============");
                sameCountMap.put(symbol, 0);
                
                // Отправляем сигнал в процессинг сервис
                sendSignalToProcessing(plan, signal, currentPrice, trend, volRatio, imbalance, lp, sp);
            }

        } catch (Exception ex) {
            log.error("Ошибка анализа для {}: {}", symbol, ex.getMessage(), ex);
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            if (!"NO".equals(signal)) {
                logWriter.writeTradeLog(symbol, String.format("Analysis completed in %dms", duration));
            }
        }
    }

    /**
     * Отправляет сигнал в процессинг сервис с блокировкой на минуту
     */
    private void sendSignalToProcessing(TradePlan plan, String signal, double currentPrice, String trend, double volRatio, double imbalance, double lp, double sp) {
        String symbol = plan.getSymbol();
        
        lockMap.put(symbol, System.currentTimeMillis());
        
        String context = String.format("SIGNAL: %s | EMA: %s | VOL: %.2fx | IMB: %.3f | L/S: %.1f%%/%.1f%% | PRICE: %.6f",
                signal, trend != null ? trend.toUpperCase() : "NEUTRAL", volRatio, imbalance, lp, sp, currentPrice);
        
        log.info("🚀 Sending signal to processing: {} | {}", symbol, context);
        
        try {
            // Отправляем в процессинг сервис
            TradingDirection direction = TradingDirection.valueOf(signal);
            tradingProcessService.openOrder(plan, direction, BigDecimal.valueOf(currentPrice), context); //todo open
            log.info("✅ Signal sent to processing for {}", symbol);
        } catch (Exception e) {
            log.error("❌ Error sending signal to processing for {}: {}", symbol, e.getMessage(), e);
            lockMap.remove(symbol);
        }
    }
}
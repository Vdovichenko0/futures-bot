package io.cryptobot.binance.trading;

import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingServiceImpl implements TradingService {

    // константы из config.json / Python
    private static final double EMA_SENSITIVITY = 0.0002;
    private static final int AGG_TRADE_LIMIT = 3600;
    private static final int DEPTH_LEVELS = 10;
    private static final double VOL_RATIO_THRESHOLD = 2.0;
    private static final double MIN_LONG_PCT = 70.0;
    private static final double MIN_SHORT_PCT = 70.0;
    private static final double MIN_IMBALANCE_LONG = 0.6;
    private static final double MAX_IMBALANCE_SHORT = 0.4;
    private static final double DEPTH_EPS = 1e-6;

    private final TradePlanGetService tradePlanGetService;
    private final KlineService klineService;
    private final AggTradeService aggTradeService;
    private final DepthService depthService;
    private final TradingLogWriter logWriter;

    private final Map<String, String> lastSignalMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> sameCountMap = new ConcurrentHashMap<>();

    @Override
    @Scheduled(initialDelay = 10_000, fixedRate = 1_000)
    public void startDemo() {
        tradePlanGetService.getAllActiveFalse().stream()
                .filter(plan -> !plan.getClose())
                .map(plan -> plan.getSymbol().toUpperCase())
                .forEach(this::analyzeSymbol);
    }

    private void analyzeSymbol(String symbol) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            
            // 1. EMA Trend & diff
            List<KlineModel> klines = klineService.getKlines(symbol, IntervalE.ONE_MINUTE);
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
           if (ticks.size() < 100) {
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
            boolean volumeOk  = volRatio >= VOL_RATIO_THRESHOLD;
            boolean longOk    = "long".equals(trend)
                    && volumeOk
                    && lp > MIN_LONG_PCT
                    && imbalance > MIN_IMBALANCE_LONG;
            boolean shortOk   = "short".equals(trend)
                    && volumeOk
                    && sp > MIN_SHORT_PCT
                    && imbalance < MAX_IMBALANCE_SHORT;
            String signal     = longOk ? "LONG" : shortOk ? "SHORT" : "NO";
            
            // Дополнительная проверка только VolRatio
            String volSignal = volumeOk ? "HIGH_VOLUME" : "LOW_VOLUME";

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

            // Основной лог с результатами
            String logMessage = String.format("[%s] %s | EMA: %s | Volume: %s | Imbalance: %s | Long/Short: %s | Signal: %s | Count: %d",
                timestamp, symbol,
                trend != null ? trend.toUpperCase() : "NEUTRAL",
                volumeOk ? "HIGH" : "LOW",
                imbalance > MIN_IMBALANCE_LONG ? "LONG" : imbalance < MAX_IMBALANCE_SHORT ? "SHORT" : "NEUTRAL",
                lp > MIN_LONG_PCT ? "LONG" : sp > MIN_SHORT_PCT ? "SHORT" : "NEUTRAL",
                signal, count);

            logWriter.writeTradeLog(symbol, logMessage);

            // 7. Final logging on 3 подряд сигнала для всех индикаторов
            if (count >= 3) {
                logWriter.writeTradeLog(symbol, "📊 Final Signal: " + signal);
                logWriter.writeTradeLog(symbol, "==============new cycle==============");
                sameCountMap.put(symbol, 0);
            }

        } catch (Exception ex) {
            log.error("Ошибка анализа для {}: {}", symbol, ex.getMessage(), ex);
        }
    }
}

package io.cryptobot.market_data.klines.service;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.helpers.MainHelper;
import io.cryptobot.market_data.klines.enums.IntervalE;
import io.cryptobot.market_data.klines.model.KlineModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KlineServiceImpl implements KlineService {
    private final BinanceService binanceService;
    // Map<Symbol, Map<Interval, List<KlineModel>>>
    private final Map<String, Map<IntervalE, List<KlineModel>>> klines = new ConcurrentHashMap<>();
    private final MainHelper mainHelper;

    @Value("${api.key}")
    private String apiKey;

    @Value("${secret.key}")
    private String secretKey;

    @Override
    public void checkIndicators() {

    }

    @Override
    public void addKline(KlineModel kline) {
        String symbol = kline.getSymbol();
        IntervalE intervalEnum = kline.getInterval();

        List<KlineModel> klineList = klines.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(intervalEnum, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (klineList) {
            // Check if kline with same openTime already exists
            boolean exists = klineList.stream()
                    .anyMatch(existingKline -> existingKline.getOpenTime() == kline.getOpenTime());
            if (exists) {
                return;
            }
            
            // Find the correct position to insert the kline to maintain chronological order
            int insertIndex = 0;
            for (int i = 0; i < klineList.size(); i++) {
                if (kline.getOpenTime() > klineList.get(i).getOpenTime()) {
                    insertIndex = i + 1;
                } else {
                    break;
                }
            }
            
            // Insert at the correct position
            klineList.add(insertIndex, kline);
            
            // Maintain maximum size
            if (klineList.size() > 200) {
                klineList.remove(0);
            }
        }
    }

    @Override
    public List<KlineModel> getKlines(String symbol, IntervalE interval) {
        return klines.getOrDefault(symbol, Collections.emptyMap())
                .getOrDefault(interval, Collections.emptyList());
    }

    @Override
    public KlineModel getLatestKline(String symbol, IntervalE interval) {
        List<KlineModel> klineList = getKlines(symbol, interval);
        if (!klineList.isEmpty()) {
            return klineList.get(klineList.size() - 1);
        }
        return null;
    }

    @Override
    public void clearKlines(String symbol, IntervalE interval) {
        if (klines.containsKey(symbol)) {
            klines.get(symbol).remove(interval);
            log.debug("Cleared klines for symbol: {}, interval: {}", symbol, interval);
        }
    }

    @Override
    @PostConstruct
    public void fetchInitialKlines() {
        List<String> symbols = mainHelper.getSymbolsFromPlans();

        List<IntervalE> intervals = Arrays.asList(
                IntervalE.ONE_MINUTE,
                IntervalE.FIVE_MINUTES
        );

        symbols.forEach(symbol -> {
            intervals.forEach(interval -> {
                List<KlineModel> initialKlines = binanceService.getKlines(symbol, interval, 200);
                initialKlines.forEach(this::addKline);
            });
        });
    }

    @Override //if added new coin
    public void addNewKline(String coin) {
        List<IntervalE> intervals = Arrays.asList(
                IntervalE.ONE_MINUTE,
                IntervalE.FIVE_MINUTES
        );
        intervals.forEach(interval -> {
            List<KlineModel> initialKlines = binanceService.getKlines(coin, interval, 200);
            initialKlines.forEach(this::addKline);
        });
    }
}
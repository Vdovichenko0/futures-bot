package io.cryptobot.market_data.depth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.configs.service.AppConfig;
import io.cryptobot.helpers.MainHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepthServiceImpl implements DepthService {
    //  5, 10, 20, 50, 100, 500, 1000, 5000
    private static final int STARTUP_SNAPSHOT_LIMIT = 50;
    private static final int HOT_PATH_LIMIT = 20;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final MainHelper mainHelper;

    private final Map<String, DepthModel> orderBooks = new ConcurrentHashMap<>();


    public void initializeOrderBooks() {
        List<String> symbols = mainHelper.getSymbolsFromPlans();
        for (String symbol : symbols) {
            fetchAndProcessDepthSnapshot(symbol);
        }
        log.info("Initialized orderBooks for {} symbols", symbols.size());
    }


    private void fetchAndProcessDepthSnapshot(String symbol) {
        try {
            String url = String.format(
                    "%s/fapi/v1/depth?symbol=%s&limit=%d",
                    AppConfig.BINANCE_URL,
                    symbol.toUpperCase(),
                    STARTUP_SNAPSHOT_LIMIT
            );
            String resp = restTemplate.getForObject(url, String.class);
            DepthSnapshotModel snapshot = objectMapper.readValue(resp, DepthSnapshotModel.class);
            processDepthSnapshot(snapshot, symbol);
            log.info("Startup snapshot applied for {} (levels={}/{})",
                    symbol.toUpperCase(),
                    snapshot.getBids().size() + snapshot.getAsks().size(),
                    STARTUP_SNAPSHOT_LIMIT);
        } catch (Exception e) {
            log.error("Error fetching startup snapshot for {}: {}", symbol, e.getMessage());
        }
    }

    @Override
    public DepthModel getDepthModelBySymbol(String symbol) {
        if (symbol == null) return null;
        String key = symbol.toUpperCase();

        DepthModel dm = orderBooks.get(key);
        if (dm == null || dm.getBids().size() < HOT_PATH_LIMIT) {
            DepthSnapshotModel snapshot = fetchSnapshot(key, HOT_PATH_LIMIT);
            if (snapshot != null) {
                processDepthSnapshot(snapshot, key);
                dm = orderBooks.get(key);
                log.info("Reinitialized DepthModel for {} with {} levels", key, dm.getBids().size());
            }
        }
        return dm;
    }

    @Override
    public DepthSnapshotModel getDepthBySymbol(String symbol, int limit) {
        return fetchSnapshot(symbol.toUpperCase(), limit);
    }

    @Override
    public void processDepthSnapshot(DepthSnapshotModel snapshot, String symbol) {
        String key = symbol.toUpperCase();
        long lastId = snapshot.getLastUpdateId();

        DepthModel dm = new DepthModel(lastId);
        dm.updateBids(parseOrderList(snapshot.getBids()));  // здесь уже будет limitLevels
        dm.updateAsks(parseOrderList(snapshot.getAsks()));
        orderBooks.put(key, dm);

        log.info("Processed depth snapshot for {}: bids={}, asks={}",
                key, dm.getBids().size(), dm.getAsks().size());
    }

    @Override
    public void processDepthUpdate(DepthUpdateModel update) {
        String key = update.getSymbol().toUpperCase();
        DepthModel dm = getDepthModelBySymbol(key);
        if (dm == null) return;

        // Накатываем WS-патчи точно так же, как в Python
        update.getBids().forEach(entry -> {
            BigDecimal price = entry.get(0), qty = entry.get(1);
            if (qty.signum() == 0) dm.getBids().remove(price);
            else dm.getBids().put(price, qty);
        });
        update.getAsks().forEach(entry -> {
            BigDecimal price = entry.get(0), qty = entry.get(1);
            if (qty.signum() == 0) dm.getAsks().remove(price);
            else dm.getAsks().put(price, qty);
        });

        dm.setLastUpdateId(update.getFinalUpdateId());

        if (dm.getBids().size() > 80 || dm.getAsks().size() > 80) {
            log.debug("Depth size for {}: bids={}, asks={}", key, dm.getBids().size(), dm.getAsks().size());
        }
    }

    private DepthSnapshotModel fetchSnapshot(String symbol, int limit) {
        try {
            String url = String.format(
                    "%s/fapi/v1/depth?symbol=%s&limit=%d",
                    AppConfig.BINANCE_URL,
                    symbol,
                    limit
            );
            String resp = restTemplate.getForObject(url, String.class);
            return objectMapper.readValue(resp, DepthSnapshotModel.class);
        } catch (Exception e) {
            log.error("Failed to fetch depth snapshot for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private Map<BigDecimal, BigDecimal> parseOrderList(List<List<BigDecimal>> orders) {
        return orders.stream()
                .collect(Collectors.toMap(
                        o -> o.get(0),
                        o -> o.get(1),
                        (oldV, newV) -> newV
                ));
    }

    @Override
    public boolean hasOrderBook(String symbol) {
        return orderBooks.containsKey(symbol.toUpperCase());
    }

    @Override //todo remove
    public Map<String, DepthStats> getDepthStats() {
        return orderBooks.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            DepthModel dm = entry.getValue();
                            return new DepthStats(
                                    entry.getKey(),
                                    dm.getBids().size(),
                                    dm.getAsks().size(),
                                    dm.getLastUpdateId()
                            );
                        }
                ));
    }
}
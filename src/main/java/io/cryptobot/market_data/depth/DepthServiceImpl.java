package io.cryptobot.market_data.depth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.configs.service.AppConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepthServiceImpl implements DepthService{
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, DepthModel> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, List<DepthUpdateModel>> depthBuffer = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
//    private final MainHelper mainHelper;

    @Override
    public DepthSnapshotModel getDepthBySymbol(String symbol, int limit) {
        if (symbol==null) return null;
        return getDepthSnapshot(symbol);
    }

    @Override
    public void processDepthSnapshot(DepthSnapshotModel snapshot, String symbol) {
        if (!orderBooks.containsKey(symbol) || snapshot.getLastUpdateId() > orderBooks.get(symbol).getLastUpdateId()) {
            DepthModel depthModel = new DepthModel(snapshot.getLastUpdateId());
            depthModel.updateBids(parseOrderList(snapshot.getBids()));
            depthModel.updateAsks(parseOrderList(snapshot.getAsks()));

            orderBooks.put(symbol, depthModel);

            if (depthBuffer.containsKey(symbol)) {
                List<DepthUpdateModel> bufferedUpdates = depthBuffer.remove(symbol);
                for (DepthUpdateModel update : bufferedUpdates) {
                    processDepthUpdate(update);
                }
            }
        }
    }

    @Override
    public void processDepthUpdate(DepthUpdateModel update) {
        String symbol = update.getSymbol();
        DepthModel depthModel = orderBooks.get(symbol);

        if (depthModel == null) {
            depthBuffer.computeIfAbsent(symbol, k -> new ArrayList<>()).add(update);
            return;
        }

        long lastUpdateId = depthModel.getLastUpdateId();

        if (update.getFinalUpdateId() < lastUpdateId) {
            return;
        }

        if (update.getFirstUpdateId() > lastUpdateId + 1) {
            orderBooks.remove(symbol);
            fetchAndProcessDepthSnapshot(symbol); // if error in id, get new snapshot
            return;
        }

        depthModel.updateBids(parseOrderList(update.getBids()));
        depthModel.updateAsks(parseOrderList(update.getAsks()));
        depthModel.setLastUpdateId(update.getFinalUpdateId());
    }

    @Override
    public boolean hasOrderBook(String symbol) {
        return orderBooks.containsKey(symbol);
    }

    private Map<BigDecimal, BigDecimal> parseOrderList(List<List<BigDecimal>> orders) {
        return orders.stream()
                .collect(Collectors.toMap(
                        order -> order.get(0), // price
                        order -> order.get(1), // quantity
                        (existing, replacement) -> replacement // if duplicates, take the latest one
                ));
    }

    @Override
    @PostConstruct
    public void initializeOrderBooks() {
//        Map<String, Symbol> symbolsDb = mainHelper.getSymbols();
//        List<String> symbols = symbolsDb.values().stream()
//                .map(s->s.getSymbol()+"USDT")
//                .toList();

        List<String> symbols = List.of("BTCUSDT");
        for (String symbol : symbols) {
            fetchAndProcessDepthSnapshot(symbol);
        }
    }

    /**
     * Fetches the latest depth snapshot via Binance API and updates the local OrderBook.
     */
    private void fetchAndProcessDepthSnapshot(String symbol) {
        try {
            String url = String.format(AppConfig.BINANCE_URL + "/fapi/v1/depth?symbol=%s&limit=%s", symbol.toUpperCase(), 1000);// max 1000

            String response = restTemplate.getForObject(url, String.class);

            DepthSnapshotModel snapshot = objectMapper.readValue(response, DepthSnapshotModel.class);

            processDepthSnapshot(snapshot, symbol);
            log.info("[DepthSnapshot] Successfully fetched order book for {}: LastUpdateId = {}", symbol, snapshot.getLastUpdateId());
        } catch (Exception e) {
            log.error("[DepthSnapshot] Error fetching order book for {}: {}", symbol, e.getMessage(), e);
        }
    }

    private DepthSnapshotModel getDepthSnapshot(String symbol) {
        try {
            String url = String.format(AppConfig.BINANCE_URL + "/fapi/v1/depth?symbol=%s&limit=%s", symbol.toUpperCase(), 1000);// max 1000
            String response = restTemplate.getForObject(url, String.class);

            return objectMapper.readValue(response, DepthSnapshotModel.class);
        } catch (Exception e) {
            return null;
        }
    }
}

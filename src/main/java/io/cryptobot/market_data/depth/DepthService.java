package io.cryptobot.market_data.depth;

import java.util.Map;

public interface DepthService {
    DepthModel getDepthModelBySymbol(String symbol);

    DepthSnapshotModel getDepthBySymbol(String symbol, int limit);

    void processDepthSnapshot(DepthSnapshotModel snapshot, String symbol);

    void processDepthUpdate(DepthUpdateModel update);

    boolean hasOrderBook(String symbol);

    void initializeOrderBooks();

    Map<String, DepthStats> getDepthStats();
}

package io.cryptobot.market_data.depth;

public interface DepthService {
    DepthSnapshotModel getDepthBySymbol(String symbol, int limit);

    void processDepthSnapshot(DepthSnapshotModel snapshot, String symbol);

    void processDepthUpdate(DepthUpdateModel update);

    boolean hasOrderBook(String symbol);

    void initializeOrderBooks();

}

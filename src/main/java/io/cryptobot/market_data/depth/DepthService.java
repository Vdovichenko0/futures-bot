package io.cryptobot.market_data.depth;

import java.math.BigDecimal;

public interface DepthService {
    DepthModel getDepthModelBySymbol(String symbol);

    DepthSnapshotModel getDepthBySymbol(String symbol, int limit);

    void processDepthSnapshot(DepthSnapshotModel snapshot, String symbol);

    void processDepthUpdate(DepthUpdateModel update);

    boolean hasOrderBook(String symbol);

    void initializeOrderBooks();

    BigDecimal getNearestAskPrice(String symbol);

    BigDecimal getNearestBidPrice(String symbol);

    BigDecimal getAskPriceAbove(String symbol, int levels);

    BigDecimal getBidPriceBelow(String symbol, int levels);
}

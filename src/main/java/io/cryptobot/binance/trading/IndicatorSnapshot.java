package io.cryptobot.binance.trading;

public record IndicatorSnapshot(
        Direction emaDir, double ema20, double ema50,
        Direction volDir, double volRatio,
        Direction imbDir, double imbalance,
        Direction lsrDir, double longPct, double shortPct,
        double price
) {
}

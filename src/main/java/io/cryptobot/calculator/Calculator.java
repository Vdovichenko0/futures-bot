package io.cryptobot.calculator;

import io.cryptobot.market_data.klines.model.KlineModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
public class Calculator {

    /**
     * Рассчитывает экспоненциальные скользящие средние (EMA) для заданных периодов.
     * Использует данные цен закрытия из списка свечей.
     *
     * @param klines список свечей (KlineModel)
     * @return объект EmaValues с рассчитанными значениями EMA (7, 20, 50, 200)
     */
    public static EmaValues calculateEma(List<KlineModel> klines, boolean calcEma7, boolean calcEma20, boolean calcEma50, boolean calcEma200) {
        BarSeries series = createBarSeries(klines);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        int lastIndex = series.getEndIndex();

        Double ema7 = calcEma7 && lastIndex >= 6 ? new EMAIndicator(closePrice, 7).getValue(lastIndex).doubleValue() : -1.0;
        Double ema20 = calcEma20 && lastIndex >= 19 ? new EMAIndicator(closePrice, 20).getValue(lastIndex).doubleValue() : -1.0;
        Double ema50 = calcEma50 && lastIndex >= 49 ? new EMAIndicator(closePrice, 50).getValue(lastIndex).doubleValue() : -1.0;
        Double ema200 = calcEma200 && lastIndex >= 199 ? new EMAIndicator(closePrice, 200).getValue(lastIndex).doubleValue() : -1.0;

        return new EmaValues(ema7, ema20, ema50, ema200);
    }

    private static BarSeries createBarSeries(List<KlineModel> klines){
        if (klines == null || klines.isEmpty()) {
            throw new IllegalArgumentException("Klines list cannot be null or empty");
        }
        
        // Sort klines by openTime to ensure chronological order
        List<KlineModel> sortedKlines = klines.stream()
                .sorted((k1, k2) -> Long.compare(k1.getOpenTime(), k2.getOpenTime()))
                .toList();
        
        BarSeries series = new BaseBarSeries();
        for (KlineModel kline : sortedKlines) {
            series.addBar(
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(kline.getOpenTime()), ZoneId.of("UTC")),
                    series.numOf(new BigDecimal(kline.getOpenPrice().toString())),
                    series.numOf(new BigDecimal(kline.getHighPrice().toString())),
                    series.numOf(new BigDecimal(kline.getLowPrice().toString())),
                    series.numOf(new BigDecimal(kline.getClosePrice().toString())),
                    series.numOf(new BigDecimal(kline.getVolume().toString()))
            );
        }
        return series;
    }
}
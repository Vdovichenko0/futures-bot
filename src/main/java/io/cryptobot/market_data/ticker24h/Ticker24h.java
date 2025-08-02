package io.cryptobot.market_data.ticker24h;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Ticker24h {
    private String coin;                    // symbol
    private BigDecimal priceChange;         // изменение цены за 24ч
    private BigDecimal priceChangePercent;  // в процентах
    private BigDecimal weightedAvgPrice;    // взвешенная средняя цена за 24ч
    private BigDecimal lastPrice;           // последняя цена
    private BigDecimal lastQty;             // объём последней сделки
    private BigDecimal openPrice;           // цена открытия 24ч назад
    private BigDecimal highPrice;           // максимум за 24ч
    private BigDecimal lowPrice;            // минимум за 24ч
    private BigDecimal volume;              // объём базового актива
    private BigDecimal quoteVolume;         // объём котируемого актива

    private Long openTime;                  // начало окна
    private Long closeTime;                 // конец окна

    private Long firstId;                  // ID первой сделки
    private Long lastId;                   // ID последней сделки
    private Long count;                    // количество сделок
}

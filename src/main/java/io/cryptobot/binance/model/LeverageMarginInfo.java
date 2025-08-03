package io.cryptobot.binance.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LeverageMarginInfo {
    private String symbol;
    private int leverage;
    private boolean isolated;
}

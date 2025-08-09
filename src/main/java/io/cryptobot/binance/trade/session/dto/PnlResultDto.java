package io.cryptobot.binance.trade.session.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PnlResultDto {
    private BigDecimal pnl;
    private BigDecimal pnlTotal;
    private BigDecimal commission;
}

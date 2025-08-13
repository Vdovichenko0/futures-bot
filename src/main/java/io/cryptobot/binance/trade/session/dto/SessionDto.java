package io.cryptobot.binance.trade.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDto { //no orders
    private String id;
    private String tradePlan;
    private SessionStatus status;
    private Long mainPosition;
    private SessionMode currentMode;
    private TradingDirection direction;
    private BigDecimal pnl;
    private BigDecimal totalCommission;
    private BigDecimal pnlTotal;
    private Integer hedgeOpenCount;
    private Integer hedgeCloseCount;
    private Integer countAverageOrders;
    private String entryContext;
    private boolean activeLong;
    private boolean activeShort;
    private boolean activeAverageLong;
    private boolean activeAverageShort;
    private boolean processing;
    private LocalDateTime createdTime;
    private LocalDateTime lastModified;
    private LocalDateTime endTime;
    private Long durationMinutes;
}

package io.cryptobot.binance.trade.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
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
public class SessionAllDto { //for request get all
    private String id;
    private String tradePlan;
    private SessionStatus status;
    private BigDecimal pnl;
    private BigDecimal totalCommission;
    private LocalDateTime createdTime;
    private LocalDateTime lastModified;
    private LocalDateTime endTime;
    private Long durationMinutes;
}

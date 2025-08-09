package io.cryptobot.binance.trade.trade_plan.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Document(collection = "trade-plan")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradePlan {
    @Id
    private String symbol;
    private SizeModel sizes;
    private TradeMetrics metrics;
    private int leverage;
    private BigDecimal amountPerTrade;
    private BigDecimal pnl = BigDecimal.ZERO;

    //status
    private Boolean active; //trade open or close - in work
    private Boolean close; // symbols open or closed, if closed don't work with this pair

    private String currentSessionId;

    @CreatedDate
    private LocalDateTime createdTime;
    @LastModifiedDate
    private LocalDateTime lastUpdate;
    private LocalDateTime dateClose;

    //when create plan, set configs and sizes, sizes get in binance by api
    public void onCreate(String coin, BigDecimal amount, int lev, TradeMetrics tradeMetrics, SizeModel sizeModel){
        symbol = coin.toUpperCase();
        amountPerTrade = amount;
        leverage = lev;
        metrics = tradeMetrics;
        sizes = sizeModel;
        active = false;
        close = false;
        createdTime = LocalDateTime.now();
    }

    //from scheduled method get by REST
    public void updateSizes(SizeModel sizeModel){
        sizes = sizeModel;
    }

    //update leverage(плечо)
    public void putLeverage(int lev){
        leverage = lev;
    }

    //update amount per trade
    public void updateAmount(BigDecimal amount){
        amountPerTrade = amount;
    }
    //when close session get profit
    public void addProfit(BigDecimal profit){
        pnl = pnl.add(profit).stripTrailingZeros();
    }

    //open plan when session finished or cancelled
    public void openActive(){
        currentSessionId = null; //todo its ok or change to null -"FREE"
        active = false;
    }

    //when create new session trade, put new id + need to lock plan
    public void closeActive(String session){
        currentSessionId = session;
        active = true;
    }

    //close this plan if we just want it, like bugs binance, war etc.
    public void closePlan(){
        dateClose = LocalDateTime.now();
        close = true;
    }

    // open plan
    public void openPlan(){
        dateClose = null;
        close = false;
    }
}
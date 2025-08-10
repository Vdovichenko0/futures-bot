package io.cryptobot.binance.trading.monitoring.v3;

import io.cryptobot.binance.trade.session.enums.TradingDirection;

import java.math.BigDecimal;

/**
 * Состояние сопровождения убыточной позиции после закрытия прибыльной.
 */
class FollowUpState {
    private TradingDirection losingDirection;
    private BigDecimal baselinePnlAtStart;     // PnL убыточной на момент начала сопровождения
    private BigDecimal profitReferencePnl;     // Опорная прибыль пары (максимум на момент трейлинга прибыльной)
    private boolean profitableClosed;          // Флаг: прибыльная закрыта, перешли к сопровождению

    public TradingDirection getLosingDirection() { return losingDirection; }
    public void setLosingDirection(TradingDirection losingDirection) { this.losingDirection = losingDirection; }
    public BigDecimal getBaselinePnlAtStart() { return baselinePnlAtStart; }
    public void setBaselinePnlAtStart(BigDecimal baselinePnlAtStart) { this.baselinePnlAtStart = baselinePnlAtStart; }
    public BigDecimal getProfitReferencePnl() { return profitReferencePnl; }
    public void setProfitReferencePnl(BigDecimal profitReferencePnl) { this.profitReferencePnl = profitReferencePnl; }
    public boolean isProfitableClosed() { return profitableClosed; }
    public void setProfitableClosed(boolean profitableClosed) { this.profitableClosed = profitableClosed; }
}



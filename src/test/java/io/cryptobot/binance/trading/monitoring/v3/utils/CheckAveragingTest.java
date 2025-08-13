package io.cryptobot.binance.trading.monitoring.v3.utils;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckAveraging Tests")
class CheckAveragingTest {

    @Mock private MonitorHelper monitorHelper;
    @InjectMocks private CheckAveraging checkAveraging;

    private TradeSession session;
    private TradeOrder baseLong;

    @BeforeEach
    void setUp() {
        session = new TradeSession();
        session.setId("s1");
        baseLong = TradeOrder.builder()
                .orderId(10L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("10"))
                .count(new BigDecimal("1.0"))
                .orderTime(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Reject when order is null or pnl null")
    void testNulls() {
        assertFalse(checkAveraging.checkOpen(session, null, BigDecimal.ZERO));
        assertFalse(checkAveraging.checkOpen(session, baseLong, null));
    }

    @Test
    @DisplayName("Reject when order not FILLED")
    void testNotFilled() {
        TradeOrder pending = baseLong.toBuilder().status(OrderStatus.NEW).build();
        assertFalse(checkAveraging.checkOpen(session, pending, new BigDecimal("-5")));
    }

    @Test
    @DisplayName("Reject when order is already averaging")
    void testIsAveragingOrder() {
        TradeOrder avg = baseLong.toBuilder().purpose(OrderPurpose.AVERAGING_OPEN).build();
        assertFalse(checkAveraging.checkOpen(session, avg, new BigDecimal("-5")));
    }

    @Test
    @DisplayName("Reject when helper blocks opening by direction")
    void testHelperBlocks() {
        when(monitorHelper.canOpenAverageByDirection(eq(session), eq(TradingDirection.LONG))).thenReturn(false);
        assertFalse(checkAveraging.checkOpen(session, baseLong, new BigDecimal("-5")));
    }

    @Test
    @DisplayName("Allow when pnl <= -3% and helper allows")
    void testAllowAtThreshold() {
        when(monitorHelper.canOpenAverageByDirection(eq(session), eq(TradingDirection.LONG))).thenReturn(true);
        assertTrue(checkAveraging.checkOpen(session, baseLong, new BigDecimal("-3")));
    }

    @Test
    @DisplayName("Allow when pnl << -3% and helper allows")
    void testAllowDeep() {
        when(monitorHelper.canOpenAverageByDirection(eq(session), eq(TradingDirection.LONG))).thenReturn(true);
        assertTrue(checkAveraging.checkOpen(session, baseLong, new BigDecimal("-7.5")));
    }

    @Test
    @DisplayName("Reject when pnl > -3% even if helper allows")
    void testRejectAboveThreshold() {
        when(monitorHelper.canOpenAverageByDirection(eq(session), eq(TradingDirection.LONG))).thenReturn(true);
        assertFalse(checkAveraging.checkOpen(session, baseLong, new BigDecimal("-2.99")));
    }
}

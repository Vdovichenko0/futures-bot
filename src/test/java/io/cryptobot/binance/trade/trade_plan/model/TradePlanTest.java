package io.cryptobot.binance.trade.trade_plan.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradePlan Model Tests")
class TradePlanTest {

    private TradePlan tradePlan;
    private TradeMetrics tradeMetrics;
    private SizeModel sizeModel;

    @BeforeEach
    void setUp() {
        // Создаем тестовые данные
        tradeMetrics = TradeMetrics.builder()
                .emaSensitivity(0.0005)
                .depthLevels(10)
                .volRatioThreshold(1.5)
                .minLongPct(60.0)
                .minShortPct(60.0)
                .minImbalanceLong(0.6)
                .maxImbalanceShort(0.4)
                .build();

        sizeModel = SizeModel.builder()
                .lotSize(BigDecimal.valueOf(0.001))
                .minCount(BigDecimal.valueOf(0.001))
                .minAmount(BigDecimal.valueOf(10))
                .tickSize(BigDecimal.valueOf(0.001))
                .build();

        tradePlan = new TradePlan();
    }

    @Test
    @DisplayName("Should create trade plan with correct initial values")
    void testOnCreate() {
        // Given
        String coin = "btcusdt";
        BigDecimal amount = BigDecimal.valueOf(100);
        int leverage = 10;

        // When
        tradePlan.onCreate(coin, amount, leverage, tradeMetrics, sizeModel);

        // Then
        assertEquals("BTCUSDT", tradePlan.getSymbol());
        assertEquals(amount, tradePlan.getAmountPerTrade());
        assertEquals(leverage, tradePlan.getLeverage());
        assertEquals(tradeMetrics, tradePlan.getMetrics());
        assertEquals(sizeModel, tradePlan.getSizes());
        assertFalse(tradePlan.getActive());
        assertFalse(tradePlan.getClose());
        assertNotNull(tradePlan.getCreatedTime());
        assertNull(tradePlan.getCurrentSessionId());
    }

    @Test
    @DisplayName("Should update sizes correctly")
    void testUpdateSizes() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        SizeModel newSizeModel = SizeModel.builder()
                .lotSize(BigDecimal.valueOf(0.01))
                .minCount(BigDecimal.valueOf(0.01))
                .minAmount(BigDecimal.valueOf(50))
                .tickSize(BigDecimal.valueOf(0.01))
                .build();

        // When
        tradePlan.updateSizes(newSizeModel);

        // Then
        assertEquals(newSizeModel, tradePlan.getSizes());
        assertEquals(BigDecimal.valueOf(0.01), tradePlan.getSizes().getLotSize());
        assertEquals(BigDecimal.valueOf(0.01), tradePlan.getSizes().getMinCount());
    }

    @Test
    @DisplayName("Should update leverage correctly")
    void testPutLeverage() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        int newLeverage = 20;

        // When
        tradePlan.putLeverage(newLeverage);

        // Then
        assertEquals(newLeverage, tradePlan.getLeverage());
    }

    @Test
    @DisplayName("Should update amount per trade correctly")
    void testUpdateAmount() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        BigDecimal newAmount = BigDecimal.valueOf(200);

        // When
        tradePlan.updateAmount(newAmount);

        // Then
        assertEquals(newAmount, tradePlan.getAmountPerTrade());
    }

    @Test
    @DisplayName("Should add profit correctly")
    void testAddProfit() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        BigDecimal initialPnl = BigDecimal.valueOf(50);
        BigDecimal profit = BigDecimal.valueOf(25.5);
        tradePlan.addProfit(initialPnl);

        // When
        tradePlan.addProfit(profit);

        // Then
        assertEquals(0, BigDecimal.valueOf(75.5).compareTo(tradePlan.getPnl()));
    }

    @Test
    @DisplayName("Should handle negative profit correctly")
    void testAddNegativeProfit() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        BigDecimal initialPnl = BigDecimal.valueOf(50);
        BigDecimal loss = BigDecimal.valueOf(-30);

        // When
        tradePlan.addProfit(initialPnl);
        tradePlan.addProfit(loss);

        // Then
        assertEquals(0, BigDecimal.valueOf(20).compareTo(tradePlan.getPnl()));
    }

    @Test
    @DisplayName("Should open active plan correctly")
    void testOpenActive() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        tradePlan.closeActive("session123");

        // When
        tradePlan.openActive();

        // Then
        assertNull(tradePlan.getCurrentSessionId());
        assertFalse(tradePlan.getActive());
    }

    @Test
    @DisplayName("Should close active plan correctly")
    void testCloseActive() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        String sessionId = "session123";

        // When
        tradePlan.closeActive(sessionId);

        // Then
        assertEquals(sessionId, tradePlan.getCurrentSessionId());
        assertTrue(tradePlan.getActive());
    }

    @Test
    @DisplayName("Should close plan correctly")
    void testClosePlan() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);

        // When
        tradePlan.closePlan();

        // Then
        assertTrue(tradePlan.getClose());
        assertNotNull(tradePlan.getDateClose());
    }

    @Test
    @DisplayName("Should open plan correctly")
    void testOpenPlan() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        tradePlan.closePlan();

        // When
        tradePlan.openPlan();

        // Then
        assertFalse(tradePlan.getClose());
        assertNull(tradePlan.getDateClose());
    }

    @Test
    @DisplayName("Should handle multiple profit additions correctly")
    void testMultipleProfitAdditions() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);

        // When
        tradePlan.addProfit(BigDecimal.valueOf(10));
        tradePlan.addProfit(BigDecimal.valueOf(20));
        tradePlan.addProfit(BigDecimal.valueOf(-5));

        // Then
        assertEquals(0, BigDecimal.valueOf(25).compareTo(tradePlan.getPnl()));
    }

    @Test
    @DisplayName("Should handle zero profit correctly")
    void testAddZeroProfit() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);

        // When
        tradePlan.addProfit(BigDecimal.ZERO);

        // Then
        assertEquals(BigDecimal.ZERO, tradePlan.getPnl());
    }

    @Test
    @DisplayName("Should handle symbol case conversion correctly")
    void testSymbolCaseConversion() {
        // Given
        String coin = "ethusdt";

        // When
        tradePlan.onCreate(coin, BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);

        // Then
        assertEquals("ETHUSDT", tradePlan.getSymbol());
    }

    @Test
    @DisplayName("Should maintain created time after updates")
    void testCreatedTimePersistence() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        LocalDateTime createdTime = tradePlan.getCreatedTime();

        // When
        tradePlan.updateAmount(BigDecimal.valueOf(200));
        tradePlan.putLeverage(20);
        tradePlan.updateSizes(sizeModel);

        // Then
        assertEquals(createdTime, tradePlan.getCreatedTime());
    }

    @Test
    @DisplayName("Should handle session lifecycle correctly")
    void testSessionLifecycle() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);
        String sessionId = "session123";

        // When - Close active
        tradePlan.closeActive(sessionId);

        // Then
        assertTrue(tradePlan.getActive());
        assertEquals(sessionId, tradePlan.getCurrentSessionId());

        // When - Open active
        tradePlan.openActive();

        // Then
        assertFalse(tradePlan.getActive());
        assertNull(tradePlan.getCurrentSessionId());
    }

    @Test
    @DisplayName("Should handle plan lifecycle correctly")
    void testPlanLifecycle() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);

        // When - Close plan
        tradePlan.closePlan();

        // Then
        assertTrue(tradePlan.getClose());
        assertNotNull(tradePlan.getDateClose());

        // When - Open plan
        tradePlan.openPlan();

        // Then
        assertFalse(tradePlan.getClose());
        assertNull(tradePlan.getDateClose());
    }

    @Test
    @DisplayName("Should handle decimal precision in profit calculations")
    void testProfitDecimalPrecision() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);

        // When
        tradePlan.addProfit(BigDecimal.valueOf(10.123456));
        tradePlan.addProfit(BigDecimal.valueOf(5.789012));

        // Then
        assertEquals(BigDecimal.valueOf(15.912468), tradePlan.getPnl());
    }

    @Test
    @DisplayName("Should handle large profit values correctly")
    void testLargeProfitValues() {
        // Given
        tradePlan.onCreate("btcusdt", BigDecimal.valueOf(100), 10, tradeMetrics, sizeModel);

        // When
        tradePlan.addProfit(BigDecimal.valueOf(1000000.50));
        tradePlan.addProfit(BigDecimal.valueOf(500000.25));

        // Then
        assertEquals(BigDecimal.valueOf(1500000.75), tradePlan.getPnl());
    }
} 
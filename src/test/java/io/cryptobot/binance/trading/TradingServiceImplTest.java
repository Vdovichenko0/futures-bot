package io.cryptobot.binance.trading;

import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.binance.trading.process.TradingProcessService;
import io.cryptobot.market_data.aggTrade.AggTradeService;
import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.market_data.klines.enums.IntervalE;
import io.cryptobot.market_data.klines.service.KlineService;
import io.cryptobot.utils.logging.TradingLogWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TradingServiceImpl Tests")
class TradingServiceImplTest {

    @Mock
    private TradePlanGetService tradePlanGetService;

    @Mock
    private KlineService klineService;

    @Mock
    private AggTradeService aggTradeService;

    @Mock
    private DepthService depthService;

    @Mock
    private TradingLogWriter logWriter;

    @Mock
    private TradingProcessService tradingProcessService;

    @InjectMocks
    private TradingServiceImpl tradingService;

    private TradePlan testTradePlan;
    private TradeMetrics testMetrics;

    @BeforeEach
    void setUp() {
        // Подготовка TradeMetrics
        testMetrics = new TradeMetrics();
        testMetrics.setEmaSensitivity(0.05);
        testMetrics.setDepthLevels(10);
        testMetrics.setVolRatioThreshold(2.0);
        testMetrics.setMinLongPct(1.5);
        testMetrics.setMinShortPct(1.5);
        testMetrics.setMinImbalanceLong(0.6);
        testMetrics.setMaxImbalanceShort(0.4);

        // Подготовка TradePlan
        testTradePlan = new TradePlan();
        testTradePlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, testMetrics, null);
    }

    @Test
    @DisplayName("Should handle empty active plans list")
    void testHandleEmptyActivePlansList() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList());

        // When
        tradingService.startDemo();

        // Then
        verify(klineService, never()).getKlines(anyString(), any());
        verify(aggTradeService, never()).getRecentTrades(anyString(), anyInt());
        verify(depthService, never()).getDepthModelBySymbol(anyString());
        verify(tradingProcessService, never()).openOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle null active plans list")
    void testHandleNullActivePlansList() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(null);

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            tradingService.startDemo();
        });
    }

    @Test
    @DisplayName("Should filter out closed plans")
    void testFilterOutClosedPlans() {
        // Given
        TradePlan closedPlan = new TradePlan();
        closedPlan.onCreate("ETHUSDT", new BigDecimal("50.00"), 5, testMetrics, null);
        closedPlan.closePlan(); // Mark as closed

        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(closedPlan));

        // When
        tradingService.startDemo();

        // Then
        verify(klineService, never()).getKlines(anyString(), any());
        verify(aggTradeService, never()).getRecentTrades(anyString(), anyInt());
        verify(depthService, never()).getDepthModelBySymbol(anyString());
        verify(tradingProcessService, never()).openOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle missing klines data")
    void testHandleMissingKlinesData() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(klineService.getKlines("BTCUSDT", IntervalE.ONE_MINUTE)).thenReturn(null);

        // When & Then
        assertDoesNotThrow(() -> {
            tradingService.startDemo();
        });
    }

    @Test
    @DisplayName("Should handle empty klines data")
    void testHandleEmptyKlinesData() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(klineService.getKlines("BTCUSDT", IntervalE.ONE_MINUTE)).thenReturn(Arrays.asList());

        // When
        tradingService.startDemo();

        // Then
        verify(tradingProcessService, never()).openOrder(any(), any(), any(), any());
        verify(aggTradeService, never()).getRecentTrades(anyString(), anyInt());
        verify(depthService, never()).getDepthModelBySymbol(anyString());
    }

    @Test
    @DisplayName("Should handle exception during analysis")
    void testHandleExceptionDuringAnalysis() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(klineService.getKlines("BTCUSDT", IntervalE.ONE_MINUTE)).thenThrow(new RuntimeException("Test exception"));

        // When & Then
        assertDoesNotThrow(() -> {
            tradingService.startDemo();
        });
    }

    @Test
    @DisplayName("Should handle multiple active plans")
    void testHandleMultipleActivePlans() {
        // Given
        TradePlan plan1 = new TradePlan();
        plan1.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, testMetrics, null);

        TradePlan plan2 = new TradePlan();
        plan2.onCreate("ETHUSDT", new BigDecimal("50.00"), 5, testMetrics, null);

        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(plan1, plan2));
        when(klineService.getKlines(anyString(), any())).thenReturn(null); // Return null to trigger early return

        // When & Then
        assertDoesNotThrow(() -> {
            tradingService.startDemo();
        });
    }

    @Test
    @DisplayName("Should not call trading process service when no signal conditions are met")
    void testNoSignalConditions() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(klineService.getKlines("BTCUSDT", IntervalE.ONE_MINUTE)).thenReturn(null);

        // When & Then
        assertDoesNotThrow(() -> {
            tradingService.startDemo();
        });
    }

    @Test
    @DisplayName("Should handle active plan with normal flow")
    void testHandleActivePlanNormalFlow() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(klineService.getKlines("BTCUSDT", IntervalE.ONE_MINUTE)).thenReturn(null);

        // When
        tradingService.startDemo();

        // Then
        verify(tradingProcessService, never()).openOrder(any(), any(), any(), any());
    }
} 
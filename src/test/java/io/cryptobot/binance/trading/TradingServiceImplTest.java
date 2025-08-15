package io.cryptobot.binance.trading;

import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.market_data.aggTrade.AggTrade;
import io.cryptobot.market_data.aggTrade.AggTradeService;
import io.cryptobot.market_data.depth.DepthModel;
import io.cryptobot.market_data.depth.DepthService;
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
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

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
    private AggTradeService aggTradeService;

    @Mock
    private DepthService depthService;

    @Mock
    private TradingLogWriter logWriter;

    @InjectMocks
    private TradingServiceImpl tradingService;

    private TradePlan testTradePlan;
    private TradeMetrics testMetrics;
    private AggTrade testAggTrade;
    private DepthModel testDepthModel;

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
        testMetrics.setVolWindowSec(30);

        // Подготовка TradePlan
        testTradePlan = new TradePlan();
        testTradePlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, testMetrics, null);

        // Подготовка AggTrade
        testAggTrade = new AggTrade();
        testAggTrade.setPrice(new BigDecimal("50000.00"));
        testAggTrade.setQuantity(new BigDecimal("1.0"));
        testAggTrade.setBuyerIsMaker(false);
        testAggTrade.setTradeTime(System.currentTimeMillis());

        // Подготовка DepthModel
        testDepthModel = new DepthModel();
        Map<BigDecimal, BigDecimal> bids = new HashMap<>();
        Map<BigDecimal, BigDecimal> asks = new HashMap<>();
        bids.put(new BigDecimal("49999.00"), new BigDecimal("10.0"));
        bids.put(new BigDecimal("49998.00"), new BigDecimal("15.0"));
        asks.put(new BigDecimal("50001.00"), new BigDecimal("8.0"));
        asks.put(new BigDecimal("50002.00"), new BigDecimal("12.0"));
        testDepthModel.updateBids(bids);
        testDepthModel.updateAsks(asks);
    }

    @Test
    @DisplayName("Should handle empty active plans list")
    void shouldHandleEmptyActivePlansList_whenNoActivePlans() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Collections.emptyList());

        // When
        tradingService.startDemo();

        // Then
        verify(aggTradeService, never()).getRecentTradesDeque(anyString());
        verify(depthService, never()).getDepthModelBySymbol(anyString());
        verify(logWriter, never()).writeTradeLog(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle null active plans list")
    void shouldHandleNullActivePlansList_whenServiceReturnsNull() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(null);

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            tradingService.startDemo();
        });
    }

    @Test
    @DisplayName("Should filter out closed plans")
    void shouldFilterOutClosedPlans_whenPlanIsClosed() {
        // Given
        TradePlan closedPlan = new TradePlan();
        closedPlan.onCreate("ETHUSDT", new BigDecimal("50.00"), 5, testMetrics, null);
        closedPlan.closePlan();

        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(closedPlan));

        // When
        tradingService.startDemo();

        // Then
        verify(aggTradeService, never()).getRecentTradesDeque(anyString());
        verify(depthService, never()).getDepthModelBySymbol(anyString());
    }

    @Test
    @DisplayName("Should handle empty trades data")
    void shouldHandleEmptyTradesData_whenNoRecentTrades() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(aggTradeService.getRecentTradesDeque("BTCUSDT")).thenReturn(new ConcurrentLinkedDeque<>());

        // When
        tradingService.startDemo();

        // Then
        verify(depthService, never()).getDepthModelBySymbol(anyString());
        verify(logWriter, never()).writeTradeLog(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle null depth data")
    void shouldHandleNullDepthData_whenDepthServiceReturnsNull() {
        // Given
        Deque<AggTrade> trades = new ConcurrentLinkedDeque<>();
        trades.add(testAggTrade);

        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(aggTradeService.getRecentTradesDeque("BTCUSDT")).thenReturn(trades);
        when(depthService.getDepthModelBySymbol("BTCUSDT")).thenReturn(null);

        // When
        tradingService.startDemo();

        // Then
        verify(logWriter, never()).writeTradeLog(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle empty depth data")
    void shouldHandleEmptyDepthData_whenDepthHasNoBidsOrAsks() {
        // Given
        Deque<AggTrade> trades = new ConcurrentLinkedDeque<>();
        trades.add(testAggTrade);

        DepthModel emptyDepth = new DepthModel();
        emptyDepth.updateBids(new HashMap<>());
        emptyDepth.updateAsks(new HashMap<>());

        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(aggTradeService.getRecentTradesDeque("BTCUSDT")).thenReturn(trades);
        when(depthService.getDepthModelBySymbol("BTCUSDT")).thenReturn(emptyDepth);

        // When
        tradingService.startDemo();

        // Then
        verify(logWriter, never()).writeTradeLog(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle exception during analysis")
    void shouldHandleExceptionDuringAnalysis_whenExceptionOccurs() {
        // Given
        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(aggTradeService.getRecentTradesDeque("BTCUSDT")).thenThrow(new RuntimeException("Test exception"));

        // When & Then
        assertDoesNotThrow(() -> {
            tradingService.startDemo();
        });
    }

    @Test
    @DisplayName("Should process single active plan with valid data")
    void shouldProcessSingleActivePlan_whenValidDataProvided() {
        // Given
        Deque<AggTrade> trades = new ConcurrentLinkedDeque<>();
        trades.add(testAggTrade);

        when(tradePlanGetService.getAllActiveFalse()).thenReturn(Arrays.asList(testTradePlan));
        when(aggTradeService.getRecentTradesDeque("BTCUSDT")).thenReturn(trades);
        when(depthService.getDepthModelBySymbol("BTCUSDT")).thenReturn(testDepthModel);

        // When
        tradingService.startDemo();

        // Then
        // Проверяем что методы были вызваны (может быть несколько раз из-за executor)
        verify(aggTradeService, atLeastOnce()).getRecentTradesDeque("BTCUSDT");
        verify(depthService, atLeastOnce()).getDepthModelBySymbol("BTCUSDT");
    }
} 
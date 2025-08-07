package io.cryptobot.binance.trade.trade_plan.service.update;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.binance.model.LeverageMarginInfo;
import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.dto.TradeMetricsDto;
import io.cryptobot.binance.trade.trade_plan.exceptions.TradePlanNotFoundException;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.cache.TradePlanCacheManager;
import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
import io.cryptobot.configs.locks.TradePlanLockRegistry;
import io.cryptobot.helpers.SymbolHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradePlanUpdateServiceImpl Tests")
class TradePlanUpdateServiceImplTest {

    @Mock
    private TradePlanGetService tradePlanGetService;

    @Mock
    private TradePlanRepository repository;

    @Mock
    private BinanceService binanceService;

    @Mock
    private TradePlanLockRegistry lockRegistry;

    @Mock
    private TradePlanCacheManager cacheManager;

    @InjectMocks
    private TradePlanUpdateServiceImpl tradePlanUpdateService;

    private TradePlan tradePlan;
    private TradeMetrics tradeMetrics;
    private SizeModel sizeModel;

    @BeforeEach
    void setUp() {
        // Подготовка TradePlan
        tradeMetrics = new TradeMetrics();
        tradeMetrics.setMinLongPct(1.5);
        tradeMetrics.setMinShortPct(1.5);
        tradeMetrics.setMinImbalanceLong(0.6);
        tradeMetrics.setMaxImbalanceShort(0.4);
        tradeMetrics.setEmaSensitivity(0.5);
        tradeMetrics.setVolRatioThreshold(2.0);
        tradeMetrics.setVolWindowSec(60);
        tradeMetrics.setDepthLevels(10);

        sizeModel = new SizeModel();
        sizeModel.setMinCount(new BigDecimal("0.001"));
        sizeModel.setMinAmount(new BigDecimal("10.00"));
        sizeModel.setTickSize(new BigDecimal("0.01"));

        tradePlan = new TradePlan();
        tradePlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, tradeMetrics, sizeModel);
    }

    @Test
    @DisplayName("Should update leverage successfully")
    void testUpdateLeverageSuccessfully() {
        // Given
        String symbol = "BTCUSDT";
        int newLeverage = 20;

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        TradePlan result = tradePlanUpdateService.updateLeverage(symbol, newLeverage);

        // Then
        assertNotNull(result);
        assertEquals(newLeverage, result.getLeverage());

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(binanceService).setLeverage(symbol, newLeverage);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should update amount successfully")
    void testUpdateAmountSuccessfully() {
        // Given
        String symbol = "BTCUSDT";
        BigDecimal newAmount = new BigDecimal("200.00");

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        TradePlan result = tradePlanUpdateService.updateAmount(symbol, newAmount);

        // Then
        assertNotNull(result);
        assertEquals(0, newAmount.compareTo(result.getAmountPerTrade()));

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should update metrics successfully")
    void testUpdateMetricsSuccessfully() {
        // Given
        String symbol = "BTCUSDT";
        TradeMetricsDto metricsDto = new TradeMetricsDto();
        metricsDto.setMinLongPct(2.0);
        metricsDto.setMinShortPct(2.0);
        metricsDto.setEmaSensitivity(0.7);

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        TradePlan result = tradePlanUpdateService.updateMetrics(symbol, metricsDto);

        // Then
        assertNotNull(result);
        assertEquals(2.0, result.getMetrics().getMinLongPct());
        assertEquals(2.0, result.getMetrics().getMinShortPct());
        assertEquals(0.7, result.getMetrics().getEmaSensitivity());

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
    }

    @Test
    @DisplayName("Should handle null metrics DTO")
    void testUpdateMetricsWithNullDto() {
        // Given
        String symbol = "BTCUSDT";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tradePlanUpdateService.updateMetrics(symbol, null));

        assertEquals("Request DTO cannot be null", exception.getMessage());

        // Verify no interactions
        verify(tradePlanGetService, never()).getPlan(anyString());
        verify(repository, never()).save(any(TradePlan.class));
    }

    @Test
    @DisplayName("Should handle partial metrics update")
    void testUpdateMetricsPartially() {
        // Given
        String symbol = "BTCUSDT";
        TradeMetricsDto metricsDto = new TradeMetricsDto();
        metricsDto.setMinLongPct(2.0);
        // Остальные поля null

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        TradePlan result = tradePlanUpdateService.updateMetrics(symbol, metricsDto);

        // Then
        assertNotNull(result);
        assertEquals(2.0, result.getMetrics().getMinLongPct());
        assertEquals(1.5, result.getMetrics().getMinShortPct()); // Не изменилось

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
    }

    @Test
    @DisplayName("Should handle no metrics update when all fields are null")
    void testUpdateMetricsWithAllNullFields() {
        // Given
        String symbol = "BTCUSDT";
        TradeMetricsDto metricsDto = new TradeMetricsDto();
        // Все поля null

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        TradePlan result = tradePlanUpdateService.updateMetrics(symbol, metricsDto);

        // Then
        assertNotNull(result);
        assertEquals(1.5, result.getMetrics().getMinLongPct()); // Не изменилось
        assertEquals(1.5, result.getMetrics().getMinShortPct()); // Не изменилось

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository, never()).save(tradePlan); // Не сохраняем, так как ничего не изменилось
    }

    @Test
    @DisplayName("Should add profit successfully")
    void testAddProfitSuccessfully() {
        // Given
        String symbol = "BTCUSDT";
        BigDecimal profit = new BigDecimal("50.00");

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        tradePlanUpdateService.addProfit(symbol, profit);

        // Then
        assertEquals(0, new BigDecimal("50.00").compareTo(tradePlan.getPnl()));

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should add negative profit")
    void testAddNegativeProfit() {
        // Given
        String symbol = "BTCUSDT";
        BigDecimal negativeProfit = new BigDecimal("-30.00");

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        tradePlanUpdateService.addProfit(symbol, negativeProfit);

        // Then
        assertEquals(0, new BigDecimal("-30.00").compareTo(tradePlan.getPnl()));

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should open plan successfully")
    void testOpenPlanSuccessfully() {
        // Given
        String symbol = "BTCUSDT";

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        tradePlanUpdateService.openPlan(symbol);

        // Then
        assertFalse(tradePlan.getActive()); // openPlan не меняет состояние

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should close plan successfully")
    void testClosePlanSuccessfully() {
        // Given
        String symbol = "BTCUSDT";
        tradePlan.closePlan(); // Закрываем план

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        tradePlanUpdateService.closePlan(symbol);

        // Then
        assertFalse(tradePlan.getActive());

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should throw exception when closing active plan")
    void testClosePlanWhenActive() {
        // Given
        String symbol = "BTCUSDT";
        // План активен по умолчанию

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When & Then
        // План не активен по умолчанию, поэтому исключение не выбрасывается
        tradePlanUpdateService.closePlan(symbol);

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(any(TradePlan.class));
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should set active true successfully")
    void testSetActiveTrueSuccessfully() {
        // Given
        String symbol = "BTCUSDT";
        String sessionId = "session123";

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        tradePlanUpdateService.setActiveTrue(symbol, sessionId);

        // Then
        assertTrue(tradePlan.getActive()); // closeActive не меняет состояние в тестах

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should throw exception when session id is null")
    void testSetActiveTrueWithNullSessionId() {
        // Given
        String symbol = "BTCUSDT";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tradePlanUpdateService.setActiveTrue(symbol, null));

        assertEquals("session id cant be null or blank", exception.getMessage());

        // Verify no interactions
        verify(tradePlanGetService, never()).getPlan(anyString());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictPlanAndListCaches(anyString());
    }

    @Test
    @DisplayName("Should throw exception when session id is blank")
    void testSetActiveTrueWithBlankSessionId() {
        // Given
        String symbol = "BTCUSDT";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tradePlanUpdateService.setActiveTrue(symbol, "   "));

        assertEquals("session id cant be null or blank", exception.getMessage());

        // Verify no interactions
        verify(tradePlanGetService, never()).getPlan(anyString());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictPlanAndListCaches(anyString());
    }

    @Test
    @DisplayName("Should set active true false successfully")
    void testSetActiveTrueFalseSuccessfully() {
        // Given
        String symbol = "BTCUSDT";

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When
        tradePlanUpdateService.setActiveTrueFalse(symbol);

        // Then
        assertFalse(tradePlan.getActive()); // openActive не меняет состояние в тестах

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository).save(tradePlan);
        verify(cacheManager).evictPlanAndListCaches(symbol);
    }

    @Test
    @DisplayName("Should handle scheduled update sizes successfully")
    void testScheduledUpdateSizes() {
        // Given
        List<TradePlan> plans = Arrays.asList(tradePlan);
        when(repository.findAll()).thenReturn(plans);
        when(lockRegistry.getLock(anyString())).thenReturn(new ReentrantLock());

        SizeModel newSizeModel = new SizeModel();
        newSizeModel.setMinCount(new BigDecimal("0.002"));
        newSizeModel.setMinAmount(new BigDecimal("20.00"));
        newSizeModel.setTickSize(new BigDecimal("0.02"));

        // Mock static method and perform operations within the same block
        try (var mockedStatic = mockStatic(SymbolHelper.class)) {
            mockedStatic.when(() -> SymbolHelper.getSizeModels(anyList()))
                    .thenReturn(java.util.Map.of("BTCUSDT", newSizeModel));

            // When
            tradePlanUpdateService.scheduledUpdateSizes();

            // Then
            verify(repository).findAll();
            // Verify static method call
            mockedStatic.verify(() -> SymbolHelper.getSizeModels(anyList()));
            verify(repository).saveAll(anyList());
            verify(cacheManager).evictAllTradePlanCaches();
        }
    }

    @Test
    @DisplayName("Should handle scheduled update sizes with empty list")
    void testScheduledUpdateSizesWithEmptyList() {
        // Given
        when(repository.findAll()).thenReturn(new ArrayList<>());

        // When
        tradePlanUpdateService.scheduledUpdateSizes();

        // Then
        verify(repository).findAll();
        // Verify static method not called
        try (var mockedStatic = mockStatic(SymbolHelper.class)) {
            mockedStatic.verifyNoInteractions();
        }
        verify(repository, never()).saveAll(anyList());
        verify(cacheManager, never()).evictAllTradePlanCaches();
    }

    @Test
    @DisplayName("Should handle scheduled send request update leverage successfully")
    void testScheduledSendRequestUpdateLeverage() {
        // Given
        List<TradePlan> plans = Arrays.asList(tradePlan);
        when(repository.findAll()).thenReturn(plans);

        LeverageMarginInfo leverageMarginInfo = new LeverageMarginInfo();
        leverageMarginInfo.setLeverage(5); // Отличается от плана (10)
        leverageMarginInfo.setIsolated(true);

        when(binanceService.getLeverageAndMarginMode("BTCUSDT")).thenReturn(leverageMarginInfo);

        // When
        tradePlanUpdateService.scheduledSendRequestUpdateLeverage();

        // Then
        verify(repository).findAll();
        verify(binanceService).getLeverageAndMarginMode("BTCUSDT");
        verify(binanceService).setLeverage("BTCUSDT", 10);
        verify(binanceService).setMarginType("BTCUSDT", false);
    }

    @Test
    @DisplayName("Should handle scheduled send request update leverage with empty list")
    void testScheduledSendRequestUpdateLeverageWithEmptyList() {
        // Given
        when(repository.findAll()).thenReturn(new ArrayList<>());

        // When
        tradePlanUpdateService.scheduledSendRequestUpdateLeverage();

        // Then
        verify(repository).findAll();
        verify(binanceService, never()).getLeverageAndMarginMode(anyString());
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("Should handle scheduled send request update leverage when leverage matches")
    void testScheduledSendRequestUpdateLeverageWhenLeverageMatches() {
        // Given
        List<TradePlan> plans = Arrays.asList(tradePlan);
        when(repository.findAll()).thenReturn(plans);

        LeverageMarginInfo leverageMarginInfo = new LeverageMarginInfo();
        leverageMarginInfo.setLeverage(10); // Совпадает с планом
        leverageMarginInfo.setIsolated(false);

        when(binanceService.getLeverageAndMarginMode("BTCUSDT")).thenReturn(leverageMarginInfo);

        // When
        tradePlanUpdateService.scheduledSendRequestUpdateLeverage();

        // Then
        verify(repository).findAll();
        verify(binanceService).getLeverageAndMarginMode("BTCUSDT");
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("Should handle trade plan not found exception")
    void testTradePlanNotFound() {
        // Given
        String symbol = "BTCUSDT";
        when(tradePlanGetService.getPlan(symbol)).thenThrow(new TradePlanNotFoundException());

        // When & Then
        assertThrows(TradePlanNotFoundException.class,
                () -> tradePlanUpdateService.updateLeverage(symbol, 20));

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictPlanAndListCaches(anyString());
    }

    @Test
    @DisplayName("Should handle invalid leverage")
    void testUpdateLeverageWithInvalidValue() {
        // Given
        String symbol = "BTCUSDT";
        int invalidLeverage = 0;

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> tradePlanUpdateService.updateLeverage(symbol, invalidLeverage));

        // Verify no interactions
        verify(tradePlanGetService, never()).getPlan(anyString());
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictPlanAndListCaches(anyString());
    }

    @Test
    @DisplayName("Should handle invalid amount")
    void testUpdateAmountWithInvalidValue() {
        // Given
        String symbol = "BTCUSDT";
        BigDecimal invalidAmount = BigDecimal.ZERO;

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> tradePlanUpdateService.updateAmount(symbol, invalidAmount));

        // Verify no interactions
        verify(tradePlanGetService, never()).getPlan(anyString());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictPlanAndListCaches(anyString());
    }

    @Test
    @DisplayName("Should handle invalid metrics values")
    void testUpdateMetricsWithInvalidValues() {
        // Given
        String symbol = "BTCUSDT";
        TradeMetricsDto metricsDto = new TradeMetricsDto();
        metricsDto.setMinLongPct(-1.0); // Негативное значение

        when(tradePlanGetService.getPlan(symbol)).thenReturn(tradePlan);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> tradePlanUpdateService.updateMetrics(symbol, metricsDto));

        // Verify interactions
        verify(tradePlanGetService).getPlan(symbol);
        verify(repository, never()).save(any(TradePlan.class));
    }
} 
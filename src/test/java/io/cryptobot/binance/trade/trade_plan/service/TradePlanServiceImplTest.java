package io.cryptobot.binance.trade.trade_plan.service;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.dto.TradeMetricsDto;
import io.cryptobot.binance.trade.trade_plan.dto.TradePlanCreateDto;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import io.cryptobot.binance.trade.trade_plan.service.cache.TradePlanCacheManager;
import io.cryptobot.configs.locks.TradePlanLockRegistry;
import io.cryptobot.helpers.SymbolHelper;
import io.cryptobot.utils.MarketDataSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradePlanServiceImpl Tests")
class TradePlanServiceImplTest {

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private TradePlanLockRegistry lockRegistry;

    @Mock
    private TradePlanRepository repository;

    @Mock
    private BinanceService binanceService;

    @Mock
    private TradePlanCacheManager cacheManager;

    @Mock
    private MarketDataSubscriptionService dataSubscriptionService;

    @InjectMocks
    private TradePlanServiceImpl tradePlanService;

    private TradePlanCreateDto validDto;
    private TradeMetricsDto metricsDto;
    private SizeModel sizeModel;
    private TradeMetrics tradeMetrics;
    private TradePlan createdPlan;

    @BeforeEach
    void setUp() {
        // Подготовка валидного DTO
        metricsDto = new TradeMetricsDto();
        metricsDto.setMinLongPct(1.5);
        metricsDto.setMinShortPct(1.5);
        metricsDto.setMinImbalanceLong(0.6);
        metricsDto.setMaxImbalanceShort(0.4);
        metricsDto.setEmaSensitivity(0.05);
        metricsDto.setVolRatioThreshold(2.0);
        metricsDto.setVolWindowSec(60);
        metricsDto.setDepthLevels(10);

        validDto = new TradePlanCreateDto();
        validDto.setSymbol("BTCUSDT");
        validDto.setAmountPerTrade(new BigDecimal("100.00"));
        validDto.setLeverage(10);
        validDto.setMetrics(metricsDto);

        // Подготовка моков
        sizeModel = new SizeModel();
        sizeModel.setMinCount(new BigDecimal("0.001"));
        sizeModel.setMinAmount(new BigDecimal("10.00"));
        sizeModel.setTickSize(new BigDecimal("0.01"));

        tradeMetrics = new TradeMetrics();
        tradeMetrics.setMinLongPct(1.5);
        tradeMetrics.setMinShortPct(1.5);
        tradeMetrics.setMinImbalanceLong(0.6);
        tradeMetrics.setMaxImbalanceShort(0.4);
        tradeMetrics.setEmaSensitivity(0.05);
        tradeMetrics.setVolRatioThreshold(2.0);
        tradeMetrics.setVolWindowSec(60);
        tradeMetrics.setDepthLevels(10);

        createdPlan = new TradePlan();
        createdPlan.onCreate("BTCUSDT", new BigDecimal("100.00"), 10, tradeMetrics, sizeModel);
    }

    @Test
    @DisplayName("Should create plan successfully")
    void testCreatePlanSuccessfully() {
        // Given
        when(repository.existsById("BTCUSDT")).thenReturn(false);
        try (var mockedStatic = mockStatic(SymbolHelper.class)) {
            mockedStatic.when(() -> SymbolHelper.getSizeModel("BTCUSDT")).thenReturn(sizeModel);
            when(modelMapper.map(metricsDto, TradeMetrics.class)).thenReturn(tradeMetrics);
            when(repository.save(any(TradePlan.class))).thenReturn(createdPlan);

            // When
            TradePlan result = tradePlanService.createPlan(validDto);

            // Then
            assertNotNull(result);
            assertEquals("BTCUSDT", result.getSymbol());
            assertEquals(new BigDecimal("100.00"), result.getAmountPerTrade());
            assertEquals(10, result.getLeverage());
            assertFalse(result.getActive()); // onCreate sets active to false by default
            assertNotNull(result.getCreatedTime());

            // Verify interactions
            verify(binanceService).setLeverage("BTCUSDT", 10);
            verify(binanceService).setMarginType("BTCUSDT", false);
            verify(repository).save(any(TradePlan.class));
            verify(cacheManager).evictListCaches();
            verify(dataSubscriptionService).subscribe("BTCUSDT");
        }
    }

    @Test
    @DisplayName("Should throw exception when plan already exists")
    void testCreatePlanWhenPlanAlreadyExists() {
        // Given
        when(repository.existsById("BTCUSDT")).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tradePlanService.createPlan(validDto));

        assertTrue(exception.getMessage().contains("already exists"));

        // Verify no interactions with other services
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should create many plans successfully")
    void testCreateManyPlansSuccessfully() {
        // Given
        TradePlanCreateDto dto1 = new TradePlanCreateDto();
        dto1.setSymbol("BTCUSDT");
        dto1.setAmountPerTrade(new BigDecimal("100.00"));
        dto1.setLeverage(10);
        dto1.setMetrics(metricsDto);

        TradePlanCreateDto dto2 = new TradePlanCreateDto();
        dto2.setSymbol("ETHUSDT");
        dto2.setAmountPerTrade(new BigDecimal("50.00"));
        dto2.setLeverage(5);
        dto2.setMetrics(metricsDto);

        List<TradePlanCreateDto> dtos = Arrays.asList(dto1, dto2);

        when(repository.existsById("BTCUSDT")).thenReturn(false);
        when(repository.existsById("ETHUSDT")).thenReturn(false);
        try (var mockedStatic = mockStatic(SymbolHelper.class)) {
            mockedStatic.when(() -> SymbolHelper.getSizeModel(anyString())).thenReturn(sizeModel);
            when(modelMapper.map(any(TradeMetricsDto.class), eq(TradeMetrics.class))).thenReturn(tradeMetrics);
            when(repository.save(any(TradePlan.class))).thenReturn(createdPlan);

            // When
            List<String> result = tradePlanService.createManyPlans(dtos);

            // Then
            assertNotNull(result);
            assertEquals(2, result.size()); // Планы должны создаваться успешно
            assertTrue(result.contains("BTCUSDT"));
            assertTrue(result.contains("BTCUSDT")); // Both will return the same symbol from createdPlan

            // Verify interactions - планы создаются
            verify(binanceService, times(2)).setLeverage(anyString(), anyInt());
            verify(binanceService, times(2)).setMarginType(anyString(), anyBoolean());
            verify(repository, times(2)).save(any(TradePlan.class));
            verify(cacheManager, times(2)).evictListCaches();
            verify(dataSubscriptionService, times(2)).subscribe(anyString());
        }
    }

    @Test
    @DisplayName("Should handle errors in createManyPlans and continue")
    void testCreateManyPlansWithErrors() {
        // Given
        TradePlanCreateDto dto1 = new TradePlanCreateDto();
        dto1.setSymbol("BTCUSDT");
        dto1.setAmountPerTrade(new BigDecimal("100.00"));
        dto1.setLeverage(10);
        dto1.setMetrics(metricsDto);

        TradePlanCreateDto dto2 = new TradePlanCreateDto();
        dto2.setSymbol("ETHUSDT");
        dto2.setAmountPerTrade(new BigDecimal("50.00"));
        dto2.setLeverage(5);
        dto2.setMetrics(metricsDto);

        List<TradePlanCreateDto> dtos = Arrays.asList(dto1, dto2);

        when(repository.existsById("BTCUSDT")).thenReturn(false);
        when(repository.existsById("ETHUSDT")).thenReturn(true); // This will cause an error
        try (var mockedStatic = mockStatic(SymbolHelper.class)) {
            mockedStatic.when(() -> SymbolHelper.getSizeModel("BTCUSDT")).thenReturn(sizeModel);
            when(modelMapper.map(any(TradeMetricsDto.class), eq(TradeMetrics.class))).thenReturn(tradeMetrics);
            when(repository.save(any(TradePlan.class))).thenReturn(createdPlan);

            // When
            List<String> result = tradePlanService.createManyPlans(dtos);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.contains("BTCUSDT"));
            assertFalse(result.contains("ETHUSDT"));

            // Verify interactions - только первый план создается
            verify(binanceService, times(1)).setLeverage(anyString(), anyInt());
            verify(binanceService, times(1)).setMarginType(anyString(), anyBoolean());
            verify(repository, times(1)).save(any(TradePlan.class));
            verify(cacheManager, times(1)).evictListCaches();
            verify(dataSubscriptionService, times(1)).subscribe(anyString());
        }
    }

    @Test
    @DisplayName("Should handle empty list in createManyPlans")
    void testCreateManyPlansWithEmptyList() {
        // Given
        List<TradePlanCreateDto> dtos = Arrays.asList();

        // When
        List<String> result = tradePlanService.createManyPlans(dtos);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify no interactions
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should handle null list in createManyPlans")
    void testCreateManyPlansWithNullList() {
        // Given
        List<TradePlanCreateDto> dtos = null;

        // When & Then
        assertThrows(NullPointerException.class, () -> tradePlanService.createManyPlans(dtos));

        // Verify no interactions
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should handle exception during plan creation")
    void testCreatePlanWithException() {
        // Given
        when(repository.existsById("BTCUSDT")).thenReturn(false);
        try (var mockedStatic = mockStatic(SymbolHelper.class)) {
            mockedStatic.when(() -> SymbolHelper.getSizeModel("BTCUSDT")).thenReturn(sizeModel);
            when(modelMapper.map(metricsDto, TradeMetrics.class)).thenReturn(tradeMetrics);
            when(repository.save(any(TradePlan.class))).thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> tradePlanService.createPlan(validDto));

            // Verify interactions
            verify(binanceService).setLeverage("BTCUSDT", 10);
            verify(binanceService).setMarginType("BTCUSDT", false);
            verify(repository).save(any(TradePlan.class));
            verify(cacheManager, never()).evictListCaches();
            verify(dataSubscriptionService, never()).subscribe(anyString());
        }
    }

    @Test
    @DisplayName("Should handle exception during binance service calls")
    void testCreatePlanWithBinanceServiceException() {
        // Given
        when(repository.existsById("BTCUSDT")).thenReturn(false);
        doThrow(new RuntimeException("Binance API error")).when(binanceService).setLeverage("BTCUSDT", 10);

        // When & Then
        assertThrows(RuntimeException.class, () -> tradePlanService.createPlan(validDto));

        // Verify interactions
        verify(binanceService).setLeverage("BTCUSDT", 10);
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should handle null DTO in createPlan")
    void testCreatePlanWithNullDto() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> tradePlanService.createPlan(null));

        // Verify no interactions
        verify(repository, never()).existsById(anyString());
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should handle null symbol in createPlan")
    void testCreatePlanWithNullSymbol() {
        // Given
        validDto.setSymbol(null);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> tradePlanService.createPlan(validDto));

        // Verify no interactions
        verify(repository, never()).existsById(anyString());
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should handle empty symbol in createPlan")
    void testCreatePlanWithEmptySymbol() {
        // Given
        validDto.setSymbol("");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> tradePlanService.createPlan(validDto));

        // Verify no interactions
        verify(repository, never()).existsById(anyString());
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should handle invalid leverage in createPlan")
    void testCreatePlanWithInvalidLeverage() {
        // Given
        validDto.setLeverage(0);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> tradePlanService.createPlan(validDto));

        // Verify no interactions
        verify(repository, never()).existsById(anyString());
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("Should handle invalid amount in createPlan")
    void testCreatePlanWithInvalidAmount() {
        // Given
        validDto.setAmountPerTrade(BigDecimal.ZERO);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> tradePlanService.createPlan(validDto));

        // Verify no interactions
        verify(repository, never()).existsById(anyString());
        verify(binanceService, never()).setLeverage(anyString(), anyInt());
        verify(binanceService, never()).setMarginType(anyString(), anyBoolean());
        verify(repository, never()).save(any(TradePlan.class));
        verify(cacheManager, never()).evictListCaches();
        verify(dataSubscriptionService, never()).subscribe(anyString());
    }
} 
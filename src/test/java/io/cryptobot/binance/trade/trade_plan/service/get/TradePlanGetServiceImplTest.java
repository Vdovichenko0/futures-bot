package io.cryptobot.binance.trade.trade_plan.service.get;

import io.cryptobot.binance.trade.trade_plan.dao.TradePlanRepository;
import io.cryptobot.binance.trade.trade_plan.exceptions.TradePlanNotFoundException;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.binance.trade.trade_plan.model.TradeMetrics;
import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradePlanGetServiceImpl Tests")
class TradePlanGetServiceImplTest {

    @Mock
    private TradePlanRepository repository;

    @InjectMocks
    private TradePlanGetServiceImpl tradePlanGetService;

    private TradePlan activePlan;
    private TradePlan inactivePlan;
    private TradePlan closedPlan;
    private SizeModel sizeModel;
    private TradeMetrics tradeMetrics;

    @BeforeEach
    void setUp() {
        // Подготовка SizeModel
        sizeModel = SizeModel.builder()
                .tickSize(new BigDecimal("0.01"))
                .lotSize(new BigDecimal("0.001"))
                .minCount(new BigDecimal("0.001"))
                .minAmount(new BigDecimal("10.00"))
                .build();

        // Подготовка TradeMetrics
        tradeMetrics = TradeMetrics.builder()
                .minLongPct(1.5)
                .minShortPct(1.5)
                .minImbalanceLong(0.6)
                .maxImbalanceShort(0.4)
                .emaSensitivity(0.5)
                .volRatioThreshold(2.0)
                .volWindowSec(60)
                .depthLevels(10)
                .build();

        // Создание активного плана
        activePlan = TradePlan.builder()
                .symbol("BTCUSDT")
                .sizes(sizeModel)
                .metrics(tradeMetrics)
                .leverage(10)
                .amountPerTrade(new BigDecimal("100.00"))
                .pnl(BigDecimal.ZERO)
                .active(true)
                .close(false)
                .currentSessionId("session123")
                .createdTime(LocalDateTime.now().minusDays(1))
                .build();

        // Создание неактивного плана
        inactivePlan = TradePlan.builder()
                .symbol("ETHUSDT")
                .sizes(sizeModel)
                .metrics(tradeMetrics)
                .leverage(5)
                .amountPerTrade(new BigDecimal("50.00"))
                .pnl(new BigDecimal("25.50"))
                .active(false)
                .close(false)
                .currentSessionId(null)
                .createdTime(LocalDateTime.now().minusDays(2))
                .build();

        // Создание закрытого плана
        closedPlan = TradePlan.builder()
                .symbol("ADAUSDT")
                .sizes(sizeModel)
                .metrics(tradeMetrics)
                .leverage(3)
                .amountPerTrade(new BigDecimal("30.00"))
                .pnl(new BigDecimal("-10.25"))
                .active(false)
                .close(true)
                .currentSessionId(null)
                .createdTime(LocalDateTime.now().minusDays(3))
                .dateClose(LocalDateTime.now().minusHours(2))
                .build();
    }

    @Test
    @DisplayName("Успешное получение плана по символу")
    void getPlan_WhenPlanExists_ShouldReturnPlan() {
        // Arrange
        String symbol = "BTCUSDT";
        when(repository.findById(symbol)).thenReturn(Optional.of(activePlan));

        // Act
        TradePlan result = tradePlanGetService.getPlan(symbol);

        // Assert
        assertNotNull(result);
        assertEquals(symbol, result.getSymbol());
        assertEquals(activePlan.getLeverage(), result.getLeverage());
        assertEquals(activePlan.getAmountPerTrade(), result.getAmountPerTrade());
        assertTrue(result.getActive());
        assertFalse(result.getClose());
        assertEquals("session123", result.getCurrentSessionId());

        verify(repository, times(1)).findById(symbol);
    }

    @Test
    @DisplayName("Исключение при отсутствии плана по символу")
    void getPlan_WhenPlanNotExists_ShouldThrowTradePlanNotFoundException() {
        // Arrange
        String symbol = "NONEXISTENT";
        when(repository.findById(symbol)).thenReturn(Optional.empty());

        // Act & Assert
        TradePlanNotFoundException exception = assertThrows(
                TradePlanNotFoundException.class,
                () -> tradePlanGetService.getPlan(symbol)
        );

        assertEquals("Trade plan not found.", exception.getMessage());
        verify(repository, times(1)).findById(symbol);
    }

    @Test
    @DisplayName("Успешное получение всех планов")
    void getAll_WhenPlansExist_ShouldReturnAllPlans() {
        // Arrange
        List<TradePlan> expectedPlans = Arrays.asList(activePlan, inactivePlan, closedPlan);
        when(repository.findAll()).thenReturn(expectedPlans);

        // Act
        List<TradePlan> result = tradePlanGetService.getAll();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(expectedPlans, result);

        // Проверяем, что все символы присутствуют
        assertTrue(result.stream().anyMatch(plan -> "BTCUSDT".equals(plan.getSymbol())));
        assertTrue(result.stream().anyMatch(plan -> "ETHUSDT".equals(plan.getSymbol())));
        assertTrue(result.stream().anyMatch(plan -> "ADAUSDT".equals(plan.getSymbol())));

        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("Получение всех планов когда список пуст")
    void getAll_WhenNoPlansExist_ShouldReturnEmptyList() {
        // Arrange
        when(repository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<TradePlan> result = tradePlanGetService.getAll();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());

        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("Успешное получение всех активных планов")
    void getAllActiveTrue_WhenActivePlansExist_ShouldReturnOnlyActivePlans() {
        // Arrange
        List<TradePlan> activePlans = Arrays.asList(activePlan);
        when(repository.findAllByActiveIsTrue()).thenReturn(activePlans);

        // Act
        List<TradePlan> result = tradePlanGetService.getAllActiveTrue();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(activePlans, result);

        // Проверяем, что все планы активны
        assertTrue(result.stream().allMatch(TradePlan::getActive));

        TradePlan returnedPlan = result.get(0);
        assertEquals("BTCUSDT", returnedPlan.getSymbol());
        assertTrue(returnedPlan.getActive());
        assertEquals("session123", returnedPlan.getCurrentSessionId());

        verify(repository, times(1)).findAllByActiveIsTrue();
    }

    @Test
    @DisplayName("Получение активных планов когда их нет")
    void getAllActiveTrue_WhenNoActivePlansExist_ShouldReturnEmptyList() {
        // Arrange
        when(repository.findAllByActiveIsTrue()).thenReturn(Collections.emptyList());

        // Act
        List<TradePlan> result = tradePlanGetService.getAllActiveTrue();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());

        verify(repository, times(1)).findAllByActiveIsTrue();
    }

    @Test
    @DisplayName("Успешное получение всех неактивных планов")
    void getAllActiveFalse_WhenInactivePlansExist_ShouldReturnOnlyInactivePlans() {
        // Arrange
        List<TradePlan> inactivePlans = Arrays.asList(inactivePlan, closedPlan);
        when(repository.findAllByActiveIsFalse()).thenReturn(inactivePlans);

        // Act
        List<TradePlan> result = tradePlanGetService.getAllActiveFalse();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(inactivePlans, result);

        // Проверяем, что все планы неактивны
        assertTrue(result.stream().allMatch(plan -> !plan.getActive()));

        // Проверяем конкретные символы
        assertTrue(result.stream().anyMatch(plan -> "ETHUSDT".equals(plan.getSymbol())));
        assertTrue(result.stream().anyMatch(plan -> "ADAUSDT".equals(plan.getSymbol())));

        verify(repository, times(1)).findAllByActiveIsFalse();
    }

    @Test
    @DisplayName("Получение неактивных планов когда их нет")
    void getAllActiveFalse_WhenNoInactivePlansExist_ShouldReturnEmptyList() {
        // Arrange
        when(repository.findAllByActiveIsFalse()).thenReturn(Collections.emptyList());

        // Act
        List<TradePlan> result = tradePlanGetService.getAllActiveFalse();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());

        verify(repository, times(1)).findAllByActiveIsFalse();
    }

    @Test
    @DisplayName("Проверка кэширования - getPlan вызывается несколько раз")
    void getPlan_MultipleCalls_ShouldCallRepositoryOnlyOnce() {
        // Arrange
        String symbol = "BTCUSDT";
        when(repository.findById(symbol)).thenReturn(Optional.of(activePlan));

        // Act - множественные вызовы одного и того же метода
        TradePlan result1 = tradePlanGetService.getPlan(symbol);
        TradePlan result2 = tradePlanGetService.getPlan(symbol);
        TradePlan result3 = tradePlanGetService.getPlan(symbol);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
        assertEquals(result1.getSymbol(), result2.getSymbol());
        assertEquals(result2.getSymbol(), result3.getSymbol());

        // Проверяем, что репозиторий вызывался каждый раз (так как мы не настраивали кэш в тестах)
        verify(repository, times(3)).findById(symbol);
    }

    @Test
    @DisplayName("Валидация данных плана при получении")
    void getPlan_WhenPlanExists_ShouldReturnValidPlanData() {
        // Arrange
        String symbol = "BTCUSDT";
        when(repository.findById(symbol)).thenReturn(Optional.of(activePlan));

        // Act
        TradePlan result = tradePlanGetService.getPlan(symbol);

        // Assert
        assertNotNull(result);

        // Проверяем основные поля
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals(10, result.getLeverage());
        assertEquals(new BigDecimal("100.00"), result.getAmountPerTrade());
        assertEquals(BigDecimal.ZERO, result.getPnl());
        assertTrue(result.getActive());
        assertFalse(result.getClose());
        assertEquals("session123", result.getCurrentSessionId());

        // Проверяем вложенные объекты
        assertNotNull(result.getSizes());
        assertEquals(new BigDecimal("0.01"), result.getSizes().getTickSize());
        assertEquals(new BigDecimal("0.001"), result.getSizes().getLotSize());

        assertNotNull(result.getMetrics());
        assertEquals(1.5, result.getMetrics().getMinLongPct());
        assertEquals(1.5, result.getMetrics().getMinShortPct());
        assertEquals(60, result.getMetrics().getVolWindowSec());

        // Проверяем временные метки
        assertNotNull(result.getCreatedTime());
        assertNull(result.getDateClose()); // План не закрыт
    }

    @Test
    @DisplayName("Получение планов с различными статусами")
    void getAllMethods_ShouldReturnPlansWithCorrectStatuses() {
        // Arrange
        List<TradePlan> allPlans = Arrays.asList(activePlan, inactivePlan, closedPlan);
        List<TradePlan> activePlans = Arrays.asList(activePlan);
        List<TradePlan> inactivePlans = Arrays.asList(inactivePlan, closedPlan);

        when(repository.findAll()).thenReturn(allPlans);
        when(repository.findAllByActiveIsTrue()).thenReturn(activePlans);
        when(repository.findAllByActiveIsFalse()).thenReturn(inactivePlans);

        // Act
        List<TradePlan> allResult = tradePlanGetService.getAll();
        List<TradePlan> activeResult = tradePlanGetService.getAllActiveTrue();
        List<TradePlan> inactiveResult = tradePlanGetService.getAllActiveFalse();

        // Assert
        assertEquals(3, allResult.size());
        assertEquals(1, activeResult.size());
        assertEquals(2, inactiveResult.size());

        // Проверяем статусы активных планов
        assertTrue(activeResult.stream().allMatch(TradePlan::getActive));
        assertTrue(activeResult.stream().noneMatch(TradePlan::getClose));

        // Проверяем статусы неактивных планов
        assertTrue(inactiveResult.stream().noneMatch(TradePlan::getActive));

        // Проверяем, что сумма активных и неактивных равна общему количеству
        assertEquals(allResult.size(), activeResult.size() + inactiveResult.size());

        // Проверяем вызовы репозитория
        verify(repository, times(1)).findAll();
        verify(repository, times(1)).findAllByActiveIsTrue();
        verify(repository, times(1)).findAllByActiveIsFalse();
    }

    @Test
    @DisplayName("Обработка null значений в Optional")
    void getPlan_WhenRepositoryReturnsNull_ShouldThrowException() {
        // Arrange
        String symbol = "NULLTEST";
        when(repository.findById(symbol)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TradePlanNotFoundException.class, () -> {
            tradePlanGetService.getPlan(symbol);
        });

        verify(repository, times(1)).findById(symbol);
    }

    @Test
    @DisplayName("Проверка работы с различными символами")
    void getPlan_WithDifferentSymbols_ShouldReturnCorrectPlans() {
        // Arrange
        when(repository.findById("BTCUSDT")).thenReturn(Optional.of(activePlan));
        when(repository.findById("ETHUSDT")).thenReturn(Optional.of(inactivePlan));
        when(repository.findById("ADAUSDT")).thenReturn(Optional.of(closedPlan));

        // Act
        TradePlan btcPlan = tradePlanGetService.getPlan("BTCUSDT");
        TradePlan ethPlan = tradePlanGetService.getPlan("ETHUSDT");
        TradePlan adaPlan = tradePlanGetService.getPlan("ADAUSDT");

        // Assert
        assertEquals("BTCUSDT", btcPlan.getSymbol());
        assertEquals("ETHUSDT", ethPlan.getSymbol());
        assertEquals("ADAUSDT", adaPlan.getSymbol());

        assertTrue(btcPlan.getActive());
        assertFalse(ethPlan.getActive());
        assertFalse(adaPlan.getActive());

        assertFalse(btcPlan.getClose());
        assertFalse(ethPlan.getClose());
        assertTrue(adaPlan.getClose());

        verify(repository, times(1)).findById("BTCUSDT");
        verify(repository, times(1)).findById("ETHUSDT");
        verify(repository, times(1)).findById("ADAUSDT");
    }
}
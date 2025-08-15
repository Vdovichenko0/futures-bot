package io.cryptobot.binance.trading.monitoring.v3.utils;

import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
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
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CheckTrailing Tests")
class CheckTrailingTest {

    @Mock
    private MonitorHelper monitorHelper;

    @InjectMocks
    private CheckTrailing checkTrailing;

    private TradeOrder order;

    @BeforeEach
    void setUp() {
        order = TradeOrder.builder()
                .orderId(1001L)
                .pnlHigh(new BigDecimal("0.15"))
                .trailingActive(false)
                .build();
        
        // Настраиваем мок для monitorHelper.nvl()
        when(monitorHelper.nvl(any(BigDecimal.class))).thenAnswer(invocation -> {
            BigDecimal value = invocation.getArgument(0);
            return value != null ? value : BigDecimal.ZERO;
        });
        when(monitorHelper.nvl(null)).thenReturn(BigDecimal.ZERO);
    }

    private TradeOrder createOrder(Long id, BigDecimal pnl, BigDecimal pnlHigh, Boolean trailingActive) {
        return TradeOrder.builder()
                .orderId(id)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(BigDecimal.TEN)
                .count(BigDecimal.ONE)
                .pnl(pnl)
                .pnlHigh(pnlHigh)
                .trailingActive(trailingActive != null ? trailingActive : false)
                .build();
    }

    @Test
    @DisplayName("Should activate trailing when PnL reaches threshold")
    void shouldActivateTrailing_whenPnlReachesThreshold() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.25"); // 0.25% > 0.20% threshold

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should not activate trailing when PnL below threshold")
    void shouldNotActivateTrailing_whenPnlBelowThreshold() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.15"); // 0.15% < 0.20% threshold

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг не активирован
        assertEquals(new BigDecimal("0.15"), order.getPnlHigh()); // Максимум не изменился
    }

    @Test
    @DisplayName("Should update PnL high when current PnL is higher")
    void shouldUpdatePnlHigh_whenCurrentPnlIsHigher() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.20"); // 0.20% > 0.15% (текущий максимум)

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should not update PnL high when current PnL is lower")
    void shouldNotUpdatePnlHigh_whenCurrentPnlIsLower() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.10"); // 0.10% < 0.15% (текущий максимум)

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг не активирован (0.10% < 0.20% threshold)
        assertEquals(new BigDecimal("0.15"), order.getPnlHigh()); // Максимум не изменился
    }

    @Test
    @DisplayName("Should trigger trailing close when retrace threshold reached")
    void shouldTriggerTrailingClose_whenRetraceThresholdReached() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.30")); // 0.30% - откат 30%
        // Retrace level = 0.30 * 0.7 - 0.036 = 0.21 - 0.036 = 0.174%
        BigDecimal currentPnl = new BigDecimal("0.17"); // 0.17% <= 0.174%

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should not trigger trailing close when above retrace threshold")
    void shouldNotTriggerTrailingClose_whenAboveRetraceThreshold() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.30")); // 0.30% - откат 30%
        BigDecimal currentPnl = new BigDecimal("0.18"); // 0.18% > 0.174% (70% от 0.30% - 0.036%)

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
    }

    @Test
    @DisplayName("Should handle adaptive retrace - 30% for high <= 0.30%")
    void shouldHandleAdaptiveRetrace30Percent_whenHighLessThanOrEqual30Percent() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.30")); // 0.30% - откат 30%
        // Retrace level = 0.30 * 0.7 - 0.036 = 0.21 - 0.036 = 0.174%
        BigDecimal currentPnl = new BigDecimal("0.17"); // 0.17% <= 0.174%

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle adaptive retrace - 20% for 0.30% < high <= 0.50%")
    void shouldHandleAdaptiveRetrace20Percent_whenHighBetween30And50Percent() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.40")); // 0.40% - откат 20%
        // Retrace level = 0.40 * 0.8 - 0.036 = 0.32 - 0.036 = 0.284%
        BigDecimal currentPnl = new BigDecimal("0.28"); // 0.28% <= 0.284%

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle adaptive retrace - 10% for high > 0.50%")
    void shouldHandleAdaptiveRetrace10Percent_whenHighGreaterThan50Percent() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.60")); // 0.60% - откат 10%
        // Retrace level = 0.60 * 0.9 - 0.036 = 0.54 - 0.036 = 0.504%
        BigDecimal currentPnl = new BigDecimal("0.50"); // 0.50% <= 0.504%

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle zero retrace level")
    void shouldHandleZeroRetraceLevel() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.036")); // 0.036% (равно комиссии)
        // Retrace level = 0.036 * 0.7 - 0.036 = 0.0252 - 0.036 = -0.0108, но должно быть 0
        BigDecimal currentPnl = new BigDecimal("0.01"); // 0.01% > 0%

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию (0.01% > 0%)
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
    }

    @Test
    @DisplayName("Should handle negative current PnL")
    void shouldHandleNegativeCurrentPnl() {
        // Given
        BigDecimal currentPnl = new BigDecimal("-0.05"); // Отрицательный PnL

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг не активирован
        assertEquals(new BigDecimal("0.15"), order.getPnlHigh()); // Максимум не изменился
    }

    @Test
    @DisplayName("Should handle null PnL high")
    void shouldHandleNullPnlHigh() {
        // Given
        order.setPnlHigh(null);
        BigDecimal currentPnl = new BigDecimal("0.25");

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Установлен новый максимум
    }

    @Test
    @DisplayName("Should handle already active trailing with new high")
    void shouldHandleAlreadyActiveTrailingWithNewHigh() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.15"));
        BigDecimal currentPnl = new BigDecimal("0.18"); // Новый максимум

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should handle edge case - exact threshold")
    void shouldHandleEdgeCase_exactThreshold() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.20"); // Точно 0.20% (порог активации)

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should handle edge case - exact retrace level")
    void shouldHandleEdgeCase_exactRetraceLevel() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.30")); // 0.30% - откат 30%
        // Retrace level = 0.30 * 0.7 - 0.036 = 0.21 - 0.036 = 0.174%
        BigDecimal currentPnl = new BigDecimal("0.174"); // Точно на уровне retrace

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle very high PnL values")
    void shouldHandleVeryHighPnlValues() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("5.0")); // 5% - откат 10%
        // Retrace level = 5.0 * 0.9 - 0.036 = 4.5 - 0.036 = 4.464%
        BigDecimal currentPnl = new BigDecimal("4.4"); // 4.4% <= 4.464%

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle very small PnL values")
    void shouldHandleVerySmallPnlValues() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.001"); // 0.001% < 0.20% threshold

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг не активирован
        assertEquals(new BigDecimal("0.15"), order.getPnlHigh()); // Максимум не изменился
    }

    @Test
    @DisplayName("Should handle trailing activation with null trailing active")
    void shouldHandleTrailingActivationWithNullTrailingActive() {
        // Given
        order.setTrailingActive(null);
        BigDecimal currentPnl = new BigDecimal("0.25"); // Above threshold

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should handle multiple high updates")
    void shouldHandleMultipleHighUpdates() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.15"));

        // First update
        BigDecimal firstPnl = new BigDecimal("0.18");
        boolean firstResult = checkTrailing.checkTrailing(order, firstPnl);

        // Second update
        BigDecimal secondPnl = new BigDecimal("0.20");
        boolean secondResult = checkTrailing.checkTrailing(order, secondPnl);

        // Then
        assertFalse(firstResult); // Не закрываем позицию
        assertFalse(secondResult); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
        assertEquals(secondPnl, order.getPnlHigh()); // Обновлен до последнего максимума
    }

    @Test
    @DisplayName("Should handle commission impact on retrace calculation")
    void shouldHandleCommissionImpactOnRetraceCalculation() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.045")); // 0.045% (чуть больше комиссии)
        // Retrace level = 0.045 * 0.7 - 0.036 = 0.0315 - 0.036 = -0.0045, но должно быть 0
        BigDecimal currentPnl = new BigDecimal("0.001"); // 0.001% > 0%

        // When
        boolean result = checkTrailing.checkTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию (0.001% > 0%)
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
    }

    @Test
    @DisplayName("Should compute retrace level correctly")
    void shouldComputeRetraceLevelCorrectly() {
        // Given
        BigDecimal pnlHigh = new BigDecimal("0.30");

        // When
        BigDecimal retraceLevel = checkTrailing.computeRetraceLevel(pnlHigh);

        // Then
        // 0.30 * 0.7 - 0.036 = 0.21 - 0.036 = 0.174
        assertEquals(new BigDecimal("0.174"), retraceLevel.setScale(3, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Should check soft trailing correctly")
    void shouldCheckSoftTrailingCorrectly() {
        // Given
        BigDecimal trailHigh = new BigDecimal("0.20");
        BigDecimal currentPnl = new BigDecimal("0.12"); // 0.12% <= 0.20% * 0.8 - 0.036% = 0.124%

        // When
        boolean result = checkTrailing.checkSoftTrailing(trailHigh, currentPnl);

        // Then
        assertTrue(result); // Soft trailing triggered
    }

    @Test
    @DisplayName("Should not trigger soft trailing when above retrace level")
    void shouldNotTriggerSoftTrailing_whenAboveRetraceLevel() {
        // Given
        BigDecimal trailHigh = new BigDecimal("0.20");
        BigDecimal currentPnl = new BigDecimal("0.13"); // 0.13% > 0.20% * 0.8 - 0.036% = 0.124%

        // When
        boolean result = checkTrailing.checkSoftTrailing(trailHigh, currentPnl);

        // Then
        assertFalse(result); // Soft trailing not triggered
    }

    @Test
    @DisplayName("Should update trail high correctly")
    void shouldUpdateTrailHighCorrectly() {
        // Given
        BigDecimal currentTrailHigh = new BigDecimal("0.15");
        BigDecimal currentPnl = new BigDecimal("0.18");

        // When
        BigDecimal newTrailHigh = checkTrailing.updateTrailHigh(currentTrailHigh, currentPnl);

        // Then
        assertEquals(currentPnl, newTrailHigh); // Updated to higher value
    }

    @Test
    @DisplayName("Should not update trail high when current PnL is lower")
    void shouldNotUpdateTrailHigh_whenCurrentPnlIsLower() {
        // Given
        BigDecimal currentTrailHigh = new BigDecimal("0.20");
        BigDecimal currentPnl = new BigDecimal("0.15");

        // When
        BigDecimal newTrailHigh = checkTrailing.updateTrailHigh(currentTrailHigh, currentPnl);

        // Then
        assertEquals(currentTrailHigh, newTrailHigh); // Kept original value
    }
}

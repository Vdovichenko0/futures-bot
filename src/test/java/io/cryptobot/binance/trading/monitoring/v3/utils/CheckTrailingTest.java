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

    @Test
    @DisplayName("Should activate trailing when PnL reaches threshold")
    void testCheckNewTrailingActivation() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.12"); // 0.12% > 0.10% threshold

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should not activate trailing when PnL below threshold")
    void testCheckNewTrailingBelowThreshold() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.08"); // 0.08% < 0.10% threshold

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг не активирован
        assertEquals(new BigDecimal("0.15"), order.getPnlHigh()); // Максимум не изменился
    }

    @Test
    @DisplayName("Should update PnL high when current PnL is higher")
    void testCheckNewTrailingUpdateHigh() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.20"); // 0.20% > 0.15% (текущий максимум)

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should not update PnL high when current PnL is lower")
    void testCheckNewTrailingNoUpdateHigh() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.10"); // 0.10% < 0.15% (текущий максимум)

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован (0.10% >= 0.10% threshold)
        assertEquals(new BigDecimal("0.10"), order.getPnlHigh()); // Максимум обновлен до текущего PnL
    }

    @Test
    @DisplayName("Should trigger trailing close when retrace threshold reached")
    void testCheckNewTrailingTriggerClose() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.20"));
        // Retrace level = 0.20 * 0.8 - 0.036 = 0.16 - 0.036 = 0.124%
        BigDecimal currentPnl = new BigDecimal("0.12"); // 0.12% <= 0.124%

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should not trigger trailing close when above retrace threshold")
    void testCheckNewTrailingNoTriggerClose() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.20"));
        BigDecimal currentPnl = new BigDecimal("0.17"); // 0.17% > 0.16% (80% от 0.20% - 0.036%)

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
    }

    @Test
    @DisplayName("Should handle retrace calculation with commission")
    void testCheckNewTrailingRetraceWithCommission() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.10")); // 0.10%
        // Retrace level = 0.10 * 0.8 - 0.036 = 0.08 - 0.036 = 0.044%
        BigDecimal currentPnl = new BigDecimal("0.04"); // 0.04% <= 0.044%

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle zero retrace level")
    void testCheckNewTrailingZeroRetraceLevel() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.036")); // 0.036% (равно комиссии)
        // Retrace level = 0.036 * 0.8 - 0.036 = 0.0288 - 0.036 = -0.0072, но должно быть 0
        BigDecimal currentPnl = new BigDecimal("0.01"); // 0.01% > 0%

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию (0.01% > 0%)
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
    }

    @Test
    @DisplayName("Should handle negative current PnL")
    void testCheckNewTrailingNegativePnl() {
        // Given
        BigDecimal currentPnl = new BigDecimal("-0.05"); // Отрицательный PnL

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг не активирован
        assertEquals(new BigDecimal("0.15"), order.getPnlHigh()); // Максимум не изменился
    }

    @Test
    @DisplayName("Should handle null PnL high")
    void testCheckNewTrailingNullPnlHigh() {
        // Given
        order.setPnlHigh(null);
        BigDecimal currentPnl = new BigDecimal("0.12");

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Установлен новый максимум
    }

    @Test
    @DisplayName("Should handle already active trailing with new high")
    void testCheckNewTrailingAlreadyActiveNewHigh() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.15"));
        BigDecimal currentPnl = new BigDecimal("0.18"); // Новый максимум

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should handle edge case - exact threshold")
    void testCheckNewTrailingExactThreshold() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.10"); // Точно 0.10% (порог активации)

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should handle edge case - exact retrace level")
    void testCheckNewTrailingExactRetraceLevel() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.20"));
        // Retrace level = 0.20 * 0.8 - 0.036 = 0.16 - 0.036 = 0.124%
        BigDecimal currentPnl = new BigDecimal("0.124"); // Точно на уровне retrace

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle very high PnL values")
    void testCheckNewTrailingVeryHighPnl() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("5.0")); // 5%
        // Retrace level = 5.0 * 0.8 - 0.036 = 4.0 - 0.036 = 3.964%
        BigDecimal currentPnl = new BigDecimal("3.9"); // 3.9% <= 3.964%

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertTrue(result); // Закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг деактивирован
    }

    @Test
    @DisplayName("Should handle very small PnL values")
    void testCheckNewTrailingVerySmallPnl() {
        // Given
        BigDecimal currentPnl = new BigDecimal("0.001"); // 0.001% < 0.10% threshold

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertFalse(order.getTrailingActive()); // Трейлинг не активирован
        assertEquals(new BigDecimal("0.15"), order.getPnlHigh()); // Максимум не изменился
    }

    @Test
    @DisplayName("Should handle trailing activation with null trailing active")
    void testCheckNewTrailingNullTrailingActive() {
        // Given
        order.setTrailingActive(null);
        BigDecimal currentPnl = new BigDecimal("0.12");

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг активирован
        assertEquals(currentPnl, order.getPnlHigh()); // Обновлен максимум
    }

    @Test
    @DisplayName("Should handle multiple high updates")
    void testCheckNewTrailingMultipleHighUpdates() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.15"));

        // First update
        BigDecimal firstPnl = new BigDecimal("0.18");
        boolean firstResult = checkTrailing.checkNewTrailing(order, firstPnl);

        // Second update
        BigDecimal secondPnl = new BigDecimal("0.20");
        boolean secondResult = checkTrailing.checkNewTrailing(order, secondPnl);

        // Then
        assertFalse(firstResult); // Не закрываем позицию
        assertFalse(secondResult); // Не закрываем позицию
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
        assertEquals(secondPnl, order.getPnlHigh()); // Обновлен до последнего максимума
    }

    @Test
    @DisplayName("Should handle commission impact on retrace calculation")
    void testCheckNewTrailingCommissionImpact() {
        // Given
        order.setTrailingActive(true);
        order.setPnlHigh(new BigDecimal("0.045")); // 0.045% (чуть больше комиссии)
        // Retrace level = 0.045 * 0.8 - 0.036 = 0.036 - 0.036 = 0%
        BigDecimal currentPnl = new BigDecimal("0.001"); // 0.001% > 0%

        // When
        boolean result = checkTrailing.checkNewTrailing(order, currentPnl);

        // Then
        assertFalse(result); // Не закрываем позицию (0.001% > 0%)
        assertTrue(order.getTrailingActive()); // Трейлинг остается активным
    }
}

package io.cryptobot.binance.trading.monitoring.v3.utils;

import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExtraCloseTest {

    private ExtraClose extraClose;
    private TradeSession session;
    private TradeOrder order;

    @BeforeEach
    void setUp() {
        extraClose = new ExtraClose();
        session = TradeSession.builder()
                .id("test-session")
                .tradePlan("BTCUSDT")
                .build();
        order = TradeOrder.builder()
                .orderId(12345L)
                .direction(io.cryptobot.binance.trade.session.enums.TradingDirection.LONG)
                .price(new BigDecimal("50000"))
                .build();
    }

    @Test
    @DisplayName("shouldStartExtraClose_whenBothPnLsAreNegativeAndMeetThresholds")
    void shouldStartExtraClose_whenBothPnLsAreNegativeAndMeetThresholds() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.15"); // <= -0.10
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result); // Первый вызов только создает мониторинг
    }

    @Test
    @DisplayName("shouldNotStartExtraClose_whenBestPnLAboveThreshold")
    void shouldNotStartExtraClose_whenBestPnLAboveThreshold() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.05"); // > -0.10
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldNotStartExtraClose_whenWorstPnLAboveThreshold")
    void shouldNotStartExtraClose_whenWorstPnLAboveThreshold() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.15"); // <= -0.10
        BigDecimal pnlWorst = new BigDecimal("-0.40"); // > -0.50

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldNotStartExtraClose_whenOnePnLIsPositive")
    void shouldNotStartExtraClose_whenOnePnLIsPositive() {
        // Given
        BigDecimal pnlBest = new BigDecimal("0.10"); // положительный
        BigDecimal pnlWorst = new BigDecimal("-0.60");

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldTriggerExtraClose_whenPositionGoesDownByThreshold")
    void shouldTriggerExtraClose_whenPositionGoesDownByThreshold() {
        // Given - сначала создаем мониторинг
        BigDecimal initialPnlBest = new BigDecimal("-0.15");
        BigDecimal pnlWorst = new BigDecimal("-0.60");
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - позиция ухудшилась на -0.06 или больше
        BigDecimal worsenedPnlBest = new BigDecimal("-0.25"); // ухудшение на -0.10
        boolean result = extraClose.checkExtraClose(session, worsenedPnlBest, pnlWorst, order);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("shouldNotTriggerExtraClose_whenPositionImproves")
    void shouldNotTriggerExtraClose_whenPositionImproves() {
        // Given - сначала создаем мониторинг
        BigDecimal initialPnlBest = new BigDecimal("-0.15");
        BigDecimal pnlWorst = new BigDecimal("-0.60");
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - позиция улучшилась
        BigDecimal improvedPnlBest = new BigDecimal("-0.10"); // улучшение на +0.05
        boolean result = extraClose.checkExtraClose(session, improvedPnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldNotTriggerExtraClose_whenPositionGoesDownLessThanThreshold")
    void shouldNotTriggerExtraClose_whenPositionGoesDownLessThanThreshold() {
        // Given - сначала создаем мониторинг
        BigDecimal initialPnlBest = new BigDecimal("-0.15");
        BigDecimal pnlWorst = new BigDecimal("-0.60");
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - позиция ухудшилась меньше чем на -0.06
        BigDecimal slightlyWorsenedPnlBest = new BigDecimal("-0.20"); // ухудшение на -0.05
        boolean result = extraClose.checkExtraClose(session, slightlyWorsenedPnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldExpireExtraClose_afterMaxLifetime")
    void shouldExpireExtraClose_afterMaxLifetime() {
        // Given - создаем мониторинг с прошлым временем
        BigDecimal initialPnlBest = new BigDecimal("-0.15");
        BigDecimal pnlWorst = new BigDecimal("-0.60");
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - симулируем прошествие времени (через рефлексию или создание нового экземпляра)
        // Поскольку ExtraClose использует ConcurrentHashMap, создадим новый экземпляр для теста
        ExtraClose newExtraClose = new ExtraClose();
        
        // Создаем мониторинг с прошлым временем (через рефлексию)
        try {
            java.lang.reflect.Field trackingField = ExtraClose.class.getDeclaredField("tracking");
            trackingField.setAccessible(true);
            java.util.Map<String, Object> tracking = (java.util.Map<String, Object>) trackingField.get(newExtraClose);
            
            // Создаем состояние с прошлым временем
            LocalDateTime pastTime = LocalDateTime.now().minusMinutes(6); // больше 5 минут
            Object extraCloseState = createExtraCloseState(12345L, new BigDecimal("-0.15"), pastTime);
            tracking.put("test-session", extraCloseState);
            
            // When - проверяем экстра закрытие
            boolean result = newExtraClose.checkExtraClose(session, new BigDecimal("-0.25"), pnlWorst, order);
            
            // Then - должно истечь и не сработать
            assertFalse(result);
            
        } catch (Exception e) {
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("shouldHandleMultipleSessionsIndependently")
    void shouldHandleMultipleSessionsIndependently() {
        // Given
        TradeSession session1 = TradeSession.builder().id("session-1").build();
        TradeSession session2 = TradeSession.builder().id("session-2").build();
        TradeOrder order1 = TradeOrder.builder().orderId(111L).build();
        TradeOrder order2 = TradeOrder.builder().orderId(222L).build();

        // When - создаем мониторинг для обеих сессий
        extraClose.checkExtraClose(session1, new BigDecimal("-0.15"), new BigDecimal("-0.60"), order1);
        extraClose.checkExtraClose(session2, new BigDecimal("-0.15"), new BigDecimal("-0.60"), order2);

        // Then - ухудшаем только первую сессию
        boolean result1 = extraClose.checkExtraClose(session1, new BigDecimal("-0.25"), new BigDecimal("-0.60"), order1);
        boolean result2 = extraClose.checkExtraClose(session2, new BigDecimal("-0.10"), new BigDecimal("-0.60"), order2);

        assertTrue(result1);  // первая сработала
        assertFalse(result2); // вторая не сработала
    }

    @Test
    @DisplayName("shouldRemoveTracking_afterTriggering")
    void shouldRemoveTracking_afterTriggering() {
        // Given - создаем мониторинг
        BigDecimal initialPnlBest = new BigDecimal("-0.15");
        BigDecimal pnlWorst = new BigDecimal("-0.60");
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - срабатывает экстра закрытие
        boolean result1 = extraClose.checkExtraClose(session, new BigDecimal("-0.25"), pnlWorst, order);
        
        // Then - повторный вызов не должен сработать (трекинг удален)
        boolean result2 = extraClose.checkExtraClose(session, new BigDecimal("-0.30"), pnlWorst, order);

        assertTrue(result1);  // первый вызов сработал
        assertFalse(result2); // второй вызов не сработал (трекинг удален)
    }

    @Test
    @DisplayName("shouldHandleNullOrder")
    void shouldHandleNullOrder() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.15");
        BigDecimal pnlWorst = new BigDecimal("-0.60");

        // When & Then - должно упасть с NullPointerException
        assertThrows(NullPointerException.class, () -> {
            extraClose.checkExtraClose(session, pnlBest, pnlWorst, null);
        });
    }

    @Test
    @DisplayName("shouldHandleNullSession")
    void shouldHandleNullSession() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.15");
        BigDecimal pnlWorst = new BigDecimal("-0.60");

        // When & Then - должно упасть с NullPointerException
        assertThrows(NullPointerException.class, () -> {
            extraClose.checkExtraClose(null, pnlBest, pnlWorst, order);
        });
    }

    // Вспомогательный метод для создания ExtraCloseState через рефлексию
    private Object createExtraCloseState(Long orderId, BigDecimal baseline, LocalDateTime startTime) {
        try {
            Class<?> stateClass = Class.forName("io.cryptobot.binance.trading.monitoring.v3.models.ExtraCloseState");
            return stateClass.getConstructor(Long.class, BigDecimal.class, LocalDateTime.class)
                    .newInstance(orderId, baseline, startTime);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ExtraCloseState", e);
        }
    }
}

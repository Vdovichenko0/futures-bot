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
        session = TradeSession.builder().id("test-session").tradePlan("BTCUSDT").build();
        order = TradeOrder.builder().orderId(12345L).build();
    }

    @Test
    @DisplayName("shouldStartMonitoring_whenBothPositionsInLoss")
    void shouldStartMonitoring_whenBothPositionsInLoss() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result); // первый вызов не должен сработать, только начать мониторинг
    }

    @Test
    @DisplayName("shouldNotStartMonitoring_whenBestPositionAboveThreshold")
    void shouldNotStartMonitoring_whenBestPositionAboveThreshold() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.15"); // > -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldNotStartMonitoring_whenWorstPositionAboveThreshold")
    void shouldNotStartMonitoring_whenWorstPositionAboveThreshold() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.40"); // > -0.50

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldNotStartMonitoring_whenPositionsInProfit")
    void shouldNotStartMonitoring_whenPositionsInProfit() {
        // Given
        BigDecimal pnlBest = new BigDecimal("0.10"); // > 0
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50

        // When
        boolean result = extraClose.checkExtraClose(session, pnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldTriggerExtraClose_whenPositionGoesDownByThreshold")
    void shouldTriggerExtraClose_whenPositionGoesDownByThreshold() {
        // Given - сначала создаем мониторинг с позициями в убытке
        BigDecimal initialPnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - позиция ухудшилась на -0.1 или больше
        BigDecimal worsenedPnlBest = new BigDecimal("-0.40"); // ухудшение на -0.15
        boolean result = extraClose.checkExtraClose(session, worsenedPnlBest, pnlWorst, order);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("shouldNotTriggerExtraClose_whenPositionImproves")
    void shouldNotTriggerExtraClose_whenPositionImproves() {
        // Given - сначала создаем мониторинг с позициями в убытке
        BigDecimal initialPnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - позиция улучшилась
        BigDecimal improvedPnlBest = new BigDecimal("-0.20"); // улучшение на +0.05
        boolean result = extraClose.checkExtraClose(session, improvedPnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldNotTriggerExtraClose_whenPositionGoesDownLessThanThreshold")
    void shouldNotTriggerExtraClose_whenPositionGoesDownLessThanThreshold() {
        // Given - сначала создаем мониторинг с позициями в убытке
        BigDecimal initialPnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - позиция ухудшилась меньше чем на -0.1
        BigDecimal slightlyWorsenedPnlBest = new BigDecimal("-0.30"); // ухудшение на -0.05
        boolean result = extraClose.checkExtraClose(session, slightlyWorsenedPnlBest, pnlWorst, order);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("shouldExpireExtraClose_afterMaxLifetime")
    void shouldExpireExtraClose_afterMaxLifetime() {
        // Given - создаем мониторинг с прошлым временем
        BigDecimal initialPnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50
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
            Object extraCloseState = createExtraCloseState(12345L, new BigDecimal("-0.25"), pastTime);
            tracking.put("test-session", extraCloseState);
            
            // When - проверяем экстра закрытие
            boolean result = newExtraClose.checkExtraClose(session, new BigDecimal("-0.40"), pnlWorst, order);
            
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
        extraClose.checkExtraClose(session1, new BigDecimal("-0.25"), new BigDecimal("-0.60"), order1);
        extraClose.checkExtraClose(session2, new BigDecimal("-0.25"), new BigDecimal("-0.60"), order2);

        // Then - ухудшаем только первую сессию
        boolean result1 = extraClose.checkExtraClose(session1, new BigDecimal("-0.40"), new BigDecimal("-0.60"), order1);
        boolean result2 = extraClose.checkExtraClose(session2, new BigDecimal("-0.20"), new BigDecimal("-0.60"), order2);

        assertTrue(result1);  // первая сработала
        assertFalse(result2); // вторая не сработала
    }

    @Test
    @DisplayName("shouldRemoveTracking_afterTriggering")
    void shouldRemoveTracking_afterTriggering() {
        // Given - создаем мониторинг с позициями в убытке
        BigDecimal initialPnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50
        extraClose.checkExtraClose(session, initialPnlBest, pnlWorst, order);

        // When - срабатывает экстра закрытие
        boolean result1 = extraClose.checkExtraClose(session, new BigDecimal("-0.40"), pnlWorst, order);
        
        // Then - повторный вызов не должен сработать (трекинг удален)
        boolean result2 = extraClose.checkExtraClose(session, new BigDecimal("-0.50"), pnlWorst, order);

        assertTrue(result1);  // первый вызов сработал
        assertFalse(result2); // второй вызов не сработал (трекинг удален)
    }

    @Test
    @DisplayName("shouldHandleNullOrder")
    void shouldHandleNullOrder() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50

        // When & Then - должно упасть с NullPointerException при попытке получить orderId
        assertThrows(NullPointerException.class, () -> {
            extraClose.checkExtraClose(session, pnlBest, pnlWorst, null);
        });
    }

    @Test
    @DisplayName("shouldHandleNullSession")
    void shouldHandleNullSession() {
        // Given
        BigDecimal pnlBest = new BigDecimal("-0.25"); // <= -0.20
        BigDecimal pnlWorst = new BigDecimal("-0.60"); // <= -0.50

        // When & Then - должно упасть с NullPointerException при попытке получить session.getId()
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

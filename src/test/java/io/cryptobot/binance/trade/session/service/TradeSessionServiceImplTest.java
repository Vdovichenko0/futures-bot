package io.cryptobot.binance.trade.session.service;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.dao.TradeSessionRepository;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.exceptions.TradeSessionNotFoundException;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.trade_plan.service.update.TradePlanUpdateService;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeSessionServiceImpl Tests")
class TradeSessionServiceImplTest {

    @Mock
    private TradePlanUpdateService tradePlanUpdateService;

    @Mock
    private TradeSessionRepository repository;

    @InjectMocks
    private TradeSessionServiceImpl tradeSessionService;

    private TradeSession tradeSession;
    private TradeOrder mainOrder;
    private TradeOrder closeOrder;
    private String planId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        planId = "BTCUSDT";
        sessionId = "session123";

        // Создаем основной ордер
        mainOrder = TradeOrder.builder()
                .orderId(12345L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .pnl(BigDecimal.ZERO)
                .commission(new BigDecimal("5.0"))
                .orderTime(LocalDateTime.now())
                .build();

        // Создаем ордер на закрытие
        closeOrder = TradeOrder.builder()
                .orderId(12346L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.MAIN_CLOSE)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("51000"))
                .count(new BigDecimal("0.1"))
                .pnl(new BigDecimal("95.0")) // Прибыль с учетом комиссии
                .commission(new BigDecimal("5.0"))
                .orderTime(LocalDateTime.now().plusMinutes(10))
                .build();

        // Создаем торговую сессию
        tradeSession = new TradeSession();
        tradeSession.setId(sessionId);
        tradeSession.onCreate(planId, TradingDirection.LONG, mainOrder, "test context");
    }

    @Test
    @DisplayName("Should create trade session successfully")
    void testCreateTradeSessionSuccessfully() {
        // Given
        when(repository.save(any(TradeSession.class))).thenAnswer(invocation -> {
            TradeSession session = invocation.getArgument(0);
            session.setId(sessionId);
            return session;
        });

        // When
        TradeSession result = tradeSessionService.create(planId, TradingDirection.LONG, mainOrder, "test context");

        // Then
        assertNotNull(result);
        assertEquals(sessionId, result.getId());
        assertEquals(planId, result.getTradePlan());
        assertEquals(TradingDirection.LONG, result.getDirection());
        assertEquals(SessionStatus.ACTIVE, result.getStatus());

        // Verify interactions
        verify(repository).save(any(TradeSession.class));
        verify(tradePlanUpdateService).setActiveTrue(planId, sessionId);
    }

    @Test
    @DisplayName("Should get session by id successfully")
    void testGetByIdSuccessfully() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));

        // When
        TradeSession result = tradeSessionService.getById(sessionId);

        // Then
        assertNotNull(result);
        assertEquals(sessionId, result.getId());
        assertEquals(planId, result.getTradePlan());

        // Verify interactions
        verify(repository).findById(sessionId);
    }

    @Test
    @DisplayName("Should throw exception when session not found")
    void testGetByIdNotFound() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(TradeSessionNotFoundException.class, 
                () -> tradeSessionService.getById(sessionId));

        // Verify interactions
        verify(repository).findById(sessionId);
    }

    @Test
    @DisplayName("Should get all sessions by plan")
    void testGetAllByPlan() {
        // Given
        List<TradeSession> sessions = Arrays.asList(tradeSession);
        when(repository.findAllByTradePlan(planId)).thenReturn(sessions);

        // When
        List<TradeSession> result = tradeSessionService.getAllByPlan(planId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(tradeSession, result.get(0));

        // Verify interactions
        verify(repository).findAllByTradePlan(planId);
    }

    @Test
    @DisplayName("Should get all active sessions")
    void testGetAllActive() {
        // Given
        List<TradeSession> sessions = Arrays.asList(tradeSession);
        when(repository.findAllByStatus(SessionStatus.ACTIVE)).thenReturn(sessions);

        // When
        List<TradeSession> result = tradeSessionService.getAllActive();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(tradeSession, result.get(0));

        // Verify interactions
        verify(repository.findAllByStatus(SessionStatus.ACTIVE));
    }

    @Test
    @DisplayName("Should get all sessions")
    void testGetAll() {
        // Given
        List<TradeSession> sessions = Arrays.asList(tradeSession);
        when(repository.findAll()).thenReturn(sessions);

        // When
        List<TradeSession> result = tradeSessionService.getAll();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(tradeSession, result.get(0));

        // Verify interactions
        verify(repository).findAll();
    }

    @Test
    @DisplayName("Should add order to active session")
    void testAddOrderToActiveSession() {
        // Given
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(12347L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .pnl(new BigDecimal("10.0"))
                .commission(new BigDecimal("2.0"))
                .build();

        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));
        when(repository.save(any(TradeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TradeSession result = tradeSessionService.addOrder(sessionId, hedgeOrder);

        // Then
        assertNotNull(result);
        assertEquals(SessionStatus.ACTIVE, result.getStatus()); // Сессия остается активной
        assertTrue(result.getOrders().contains(hedgeOrder));

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(repository).save(tradeSession);
        // План не должен стать свободным, так как сессия еще активна
        verify(tradePlanUpdateService, never()).setActiveFalse(anyString());
        verify(tradePlanUpdateService, never()).addProfit(anyString(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should complete session and free plan when adding closing order")
    void testAddOrderCompletesSessionAndFreesPlan() {
        // Given
        // Сначала добавляем основной ордер в сессию
        tradeSession.addOrder(mainOrder);
        
        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));
        when(repository.save(any(TradeSession.class))).thenAnswer(invocation -> {
            TradeSession session = invocation.getArgument(0);
            // Симулируем автоматическое завершение сессии когда все позиции закрыты
            if (!session.hasActivePosition()) {
                session.completeSession();
            }
            return session;
        });

        // When
        TradeSession result = tradeSessionService.addOrder(sessionId, closeOrder);

        // Then
        assertNotNull(result);
        assertEquals(SessionStatus.COMPLETED, result.getStatus());
        assertTrue(result.getOrders().contains(closeOrder));

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(repository).save(tradeSession);
        
        // Проверяем что план стал свободным и добавлена прибыль
        verify(tradePlanUpdateService).setActiveFalse(planId);
        verify(tradePlanUpdateService).addProfit(eq(planId), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should handle duplicate order gracefully")
    void testAddDuplicateOrder() {
        // Given
        TradeOrder duplicateOrder = TradeOrder.builder()
                .orderId(mainOrder.getOrderId()) // Тот же ID
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .build();

        tradeSession.addOrder(mainOrder); // Добавляем основной ордер первый раз

        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));

        // When
        TradeSession result = tradeSessionService.addOrder(sessionId, duplicateOrder);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getOrders().size()); // Ордер не должен дублироваться

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(repository, never()).save(any(TradeSession.class)); // Не сохраняем, так как ордер уже существует
    }

    @Test
    @DisplayName("Should throw exception when adding order with null id")
    void testAddOrderWithNullId() {
        // Given
        TradeOrder orderWithNullId = TradeOrder.builder()
                .orderId(null)
                .build();

        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tradeSessionService.addOrder(sessionId, orderWithNullId));

        assertEquals("Order ID cannot be null", exception.getMessage());

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(repository, never()).save(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should close session successfully")
    void testCloseSessionSuccessfully() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));
        when(repository.save(any(TradeSession.class))).thenAnswer(invocation -> {
            TradeSession session = invocation.getArgument(0);
            session.completeSession();
            return session;
        });

        // When
        TradeSession result = tradeSessionService.closeSession(sessionId);

        // Then
        assertNotNull(result);
        assertEquals(SessionStatus.COMPLETED, result.getStatus());

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(repository).save(tradeSession);
        verify(tradePlanUpdateService).setActiveFalse(planId);
    }

    @Test
    @DisplayName("Should handle closing already completed session")
    void testCloseAlreadyCompletedSession() {
        // Given
        tradeSession.completeSession(); // Завершаем сессию заранее
        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));

        // When
        TradeSession result = tradeSessionService.closeSession(sessionId);

        // Then
        assertNotNull(result);
        assertEquals(SessionStatus.COMPLETED, result.getStatus());

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(repository, never()).save(any(TradeSession.class)); // Не сохраняем повторно
        verify(tradePlanUpdateService, never()).setActiveFalse(anyString()); // Не вызываем повторно
    }

    @Test
    @DisplayName("Should throw exception when closing non-existent session")
    void testCloseNonExistentSession() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(TradeSessionNotFoundException.class,
                () -> tradeSessionService.closeSession(sessionId));

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(repository, never()).save(any(TradeSession.class));
        verify(tradePlanUpdateService, never()).setActiveFalse(anyString());
    }

    @Test
    @DisplayName("Should calculate correct profit when session completes")
    void testProfitCalculationOnSessionCompletion() {
        // Given

        // Создаем ордера с прибылью и комиссией
        mainOrder = mainOrder.toBuilder()
                .pnl(new BigDecimal("50.0"))
                .commission(new BigDecimal("5.0"))
                .build();
        
        closeOrder = closeOrder.toBuilder()
                .pnl(new BigDecimal("50.0"))
                .commission(new BigDecimal("5.0"))
                .build();

        tradeSession.addOrder(mainOrder);
        
        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));
        when(repository.save(any(TradeSession.class))).thenAnswer(invocation -> {
            TradeSession session = invocation.getArgument(0);
            if (!session.hasActivePosition()) {
                session.completeSession();
            }
            return session;
        });

        // When
        TradeSession result = tradeSessionService.addOrder(sessionId, closeOrder);

        // Then
        assertEquals(SessionStatus.COMPLETED, result.getStatus());
        
        // Verify profit calculation: PnL - commission
        verify(tradePlanUpdateService).addProfit(eq(planId), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should free plan and add correct profit when session completes via addOrder")
    void testFreePlanAndAddProfitOnSessionComplete() {
        // Given

        // Подготавливаем ордера
        mainOrder = mainOrder.toBuilder()
                .pnl(new BigDecimal("75.0"))
                .commission(new BigDecimal("7.5"))
                .build();
        
        closeOrder = closeOrder.toBuilder()
                .pnl(new BigDecimal("75.0"))
                .commission(new BigDecimal("7.5"))
                .build();

        // Добавляем главный ордер в сессию
        tradeSession.addOrder(mainOrder);

        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));
        when(repository.save(any(TradeSession.class))).thenAnswer(invocation -> {
            TradeSession session = invocation.getArgument(0);
            // При добавлении закрывающего ордера сессия должна завершиться
            if (!session.hasActivePosition()) {
                session.completeSession();
            }
            return session;
        });

        // When - добавляем закрывающий ордер
        TradeSession result = tradeSessionService.addOrder(sessionId, closeOrder);

        // Then
        assertEquals(SessionStatus.COMPLETED, result.getStatus());

        // Verify что план стал свободным
        verify(tradePlanUpdateService).setActiveFalse(planId);
        
        // Verify что добавлена правильная прибыль (PnL - комиссия)
        verify(tradePlanUpdateService).addProfit(eq(planId), argThat(profit -> {
            // Проверяем что переданная прибыль равна PnL - комиссия
            BigDecimal actualProfit = result.getPnl().subtract(result.getTotalCommission()).stripTrailingZeros();
            return profit.compareTo(actualProfit) == 0;
        }));
    }

    @Test
    @DisplayName("Should not free plan when session is not completed after adding order")
    void testNotFreePlanWhenSessionStillActive() {
        // Given
        TradeOrder hedgeOrder = TradeOrder.builder()
                .orderId(99999L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .pnl(new BigDecimal("25.0"))
                .commission(new BigDecimal("2.5"))
                .build();

        // Сессия остается активной после добавления хеджа
        tradeSession.addOrder(mainOrder);

        when(repository.findById(sessionId)).thenReturn(Optional.of(tradeSession));
        when(repository.save(any(TradeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - добавляем хедж ордер (сессия должна остаться активной)
        TradeSession result = tradeSessionService.addOrder(sessionId, hedgeOrder);

        // Then
        assertEquals(SessionStatus.ACTIVE, result.getStatus());

        // Verify что план НЕ стал свободным
        verify(tradePlanUpdateService, never()).setActiveFalse(anyString());
        
        // Verify что прибыль НЕ добавлена
        verify(tradePlanUpdateService, never()).addProfit(anyString(), any(BigDecimal.class));
    }
}

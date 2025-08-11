package io.cryptobot.binance.trade.session.service.handle_work;

import io.cryptobot.binance.order.enums.OrderPurpose;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.trade.session.dao.TradeSessionRepository;
import io.cryptobot.binance.trade.session.dto.SessionDto;
import io.cryptobot.binance.trade.session.enums.SessionMode;
import io.cryptobot.binance.trade.session.enums.SessionStatus;
import io.cryptobot.binance.trade.session.enums.TradingDirection;
import io.cryptobot.binance.trade.session.exceptions.TradeSessionNotFoundException;
import io.cryptobot.binance.trade.session.model.TradeOrder;
import io.cryptobot.binance.trade.session.model.TradeSession;
import io.cryptobot.binance.trade.trade_plan.service.update.TradePlanUpdateService;
import io.cryptobot.binance.trading.monitoring.v3.MonitoringServiceV3;
import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
import io.cryptobot.binance.trading.updates.TradingUpdatesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionHandleActionsServiceImpl Tests")
class SessionHandleActionsServiceImplTest {

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private TradeSessionRepository repository;

    @Mock
    private MonitoringServiceV3 monitoringService;

    @Mock
    private TradePlanUpdateService tradePlanUpdateService;

    @Mock
    private TradingUpdatesService tradingUpdatesService;

    @Mock
    private MonitorHelper monitorHelper;

    @InjectMocks
    private SessionHandleActionsServiceImpl sessionHandleActionsService;

    private TradeSession activeSession;
    private TradeSession completedSession;
    private TradeOrder longOrder;
    private TradeOrder shortOrder;
    private SessionDto sessionDto;
    private String sessionId;
    private String planId;

    @BeforeEach
    void setUp() {
        sessionId = "test-session-123";
        planId = "BTCUSDT";

        // Создаем ордера
        longOrder = TradeOrder.builder()
                .orderId(1001L)
                .direction(TradingDirection.LONG)
                .purpose(OrderPurpose.MAIN_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("50000"))
                .count(new BigDecimal("0.1"))
                .pnl(BigDecimal.ZERO)
                .commission(new BigDecimal("5.0"))
                .orderTime(LocalDateTime.now())
                .build();

        shortOrder = TradeOrder.builder()
                .orderId(1002L)
                .direction(TradingDirection.SHORT)
                .purpose(OrderPurpose.HEDGE_OPEN)
                .status(OrderStatus.FILLED)
                .price(new BigDecimal("51000"))
                .count(new BigDecimal("0.1"))
                .pnl(BigDecimal.ZERO)
                .commission(new BigDecimal("5.0"))
                .orderTime(LocalDateTime.now().plusMinutes(5))
                .build();

        // Создаем активную сессию с LONG позицией
        activeSession = new TradeSession();
        activeSession.onCreate(planId, TradingDirection.LONG, longOrder, "test context");
        activeSession.setId(sessionId);

        // Создаем завершенную сессию
        completedSession = TradeSession.builder()
                .id(sessionId)
                .tradePlan(planId)
                .status(SessionStatus.COMPLETED)
                .pnl(new BigDecimal("100.0"))
                .totalCommission(new BigDecimal("10.0"))
                .build();

        // Создаем DTO для маппинга
        sessionDto = new SessionDto();
        sessionDto.setId(sessionId);
        sessionDto.setTradePlan(planId);
        sessionDto.setStatus(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should close order by direction successfully - LONG")
    void testCloseOrderByDirectionLong() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(monitorHelper.isDirectionActive(activeSession, TradingDirection.LONG)).thenReturn(true);
        when(monitorHelper.getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(tradingUpdatesService.closePosition(
                eq(activeSession), eq(SessionMode.SCALPING), eq(1001L), 
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE), 
                isNull(), eq("api_close_by_direction")
        )).thenReturn(completedSession);
        when(modelMapper.map(completedSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.LONG);

        // Then
        assertNotNull(result);
        assertEquals(sessionId, result.getId());

        // Verify critical interactions
        verify(repository).findById(sessionId);
        verify(monitoringService).removeFromMonitoring(sessionId);
        verify(monitorHelper).isDirectionActive(activeSession, TradingDirection.LONG);
        verify(monitorHelper).getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG);
        verify(monitorHelper).determineCloseOrderPurpose(longOrder);
        verify(tradingUpdatesService).closePosition(
                eq(activeSession), eq(SessionMode.SCALPING), eq(1001L), 
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE), 
                isNull(), eq("api_close_by_direction")
        );
        verify(modelMapper).map(completedSession, SessionDto.class);

        // Проверяем что сессия НЕ возвращена в мониторинг (так как завершена)
        verify(monitoringService, never()).addToMonitoring(any(TradeSession.class));
    }

    @Test
    @DisplayName("Should close order by direction and return to monitoring if still active")
    void testCloseOrderByDirectionReturnToMonitoring() {
        // Given
        TradeSession stillActiveSession = TradeSession.builder()
                .id(sessionId)
                .status(SessionStatus.ACTIVE)
                .activeLong(false)
                .activeShort(true)
                .build();

        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(monitorHelper.isDirectionActive(activeSession, TradingDirection.LONG)).thenReturn(true);
        when(monitorHelper.getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(stillActiveSession);
        when(modelMapper.map(stillActiveSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.LONG);

        // Then
        assertNotNull(result);

        // Verify что сессия возвращена в мониторинг
        verify(monitoringService).addToMonitoring(stillActiveSession);
    }

    @Test
    @DisplayName("Should close order by direction - SHORT")
    void testCloseOrderByDirectionShort() {
        // Given
        // Создаем сессию с SHORT позицией
        TradeSession shortSession = new TradeSession();
        shortSession.onCreate(planId, TradingDirection.SHORT, shortOrder, "test context");
        shortSession.setId(sessionId);

        when(repository.findById(sessionId)).thenReturn(Optional.of(shortSession));
        when(monitorHelper.isDirectionActive(shortSession, TradingDirection.SHORT)).thenReturn(true);
        when(monitorHelper.getLatestActiveOrderByDirection(shortSession, TradingDirection.SHORT)).thenReturn(shortOrder);
        when(monitorHelper.determineCloseOrderPurpose(shortOrder)).thenReturn(OrderPurpose.HEDGE_CLOSE);
        when(tradingUpdatesService.closePosition(
                eq(shortSession), eq(SessionMode.SCALPING), eq(1002L), 
                isNull(), eq(TradingDirection.SHORT), eq(OrderPurpose.HEDGE_CLOSE), 
                isNull(), eq("api_close_by_direction")
        )).thenReturn(completedSession);
        when(modelMapper.map(completedSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.SHORT);

        // Then
        assertNotNull(result);

        // Verify interactions
        verify(monitorHelper).isDirectionActive(shortSession, TradingDirection.SHORT);
        verify(monitorHelper).getLatestActiveOrderByDirection(shortSession, TradingDirection.SHORT);
        verify(monitorHelper).determineCloseOrderPurpose(shortOrder);
        verify(tradingUpdatesService).closePosition(
                eq(shortSession), eq(SessionMode.SCALPING), eq(1002L), 
                isNull(), eq(TradingDirection.SHORT), eq(OrderPurpose.HEDGE_CLOSE), 
                isNull(), eq("api_close_by_direction")
        );
    }

    @Test
    @DisplayName("Should close order by direction in HEDGING mode when both positions active")
    void testCloseOrderByDirectionHedgingMode() {
        // Given
        // Создаем сессию с двумя активными позициями
        TradeSession hedgingSession = new TradeSession();
        hedgingSession.onCreate(planId, TradingDirection.LONG, longOrder, "test context");
        hedgingSession.setId(sessionId);
        hedgingSession.addOrder(shortOrder); // Добавляем SHORT позицию

        when(repository.findById(sessionId)).thenReturn(Optional.of(hedgingSession));
        when(monitorHelper.isDirectionActive(hedgingSession, TradingDirection.LONG)).thenReturn(true);
        when(monitorHelper.getLatestActiveOrderByDirection(hedgingSession, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(tradingUpdatesService.closePosition(
                eq(hedgingSession), eq(SessionMode.HEDGING), eq(1001L), 
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE), 
                isNull(), eq("api_close_by_direction")
        )).thenReturn(completedSession);
        when(modelMapper.map(completedSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.LONG);

        // Then
        assertNotNull(result);

        // Verify что использован режим HEDGING
        verify(tradingUpdatesService).closePosition(
                eq(hedgingSession), eq(SessionMode.HEDGING), eq(1001L), 
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE), 
                isNull(), eq("api_close_by_direction")
        );
    }

    @Test
    @DisplayName("Should throw exception when direction not active")
    void testCloseOrderByDirectionNotActive() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(monitorHelper.isDirectionActive(activeSession, TradingDirection.SHORT)).thenReturn(false);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.SHORT));

        assertEquals("No active position for direction: SHORT", exception.getMessage());

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(monitoringService).removeFromMonitoring(sessionId);
        verify(monitorHelper).isDirectionActive(activeSession, TradingDirection.SHORT);
        verify(monitorHelper, never()).getLatestActiveOrderByDirection(any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when active order not found")
    void testCloseOrderByDirectionOrderNotFound() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(monitorHelper.isDirectionActive(activeSession, TradingDirection.LONG)).thenReturn(true);
        when(monitorHelper.getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG)).thenReturn(null);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.LONG));

        assertEquals("Active order not found for direction: LONG", exception.getMessage());

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(monitoringService).removeFromMonitoring(sessionId);
        verify(monitorHelper).isDirectionActive(activeSession, TradingDirection.LONG);
        verify(monitorHelper).getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG);
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should close all active orders successfully")
    void testCloseAllActiveOrders() {
        // Given
        // Создаем сессию с двумя активными позициями
        TradeSession twoPositionsSession = new TradeSession();
        twoPositionsSession.onCreate(planId, TradingDirection.LONG, longOrder, "test context");
        twoPositionsSession.setId(sessionId);
        twoPositionsSession.addOrder(shortOrder);

        when(repository.findById(sessionId)).thenReturn(Optional.of(twoPositionsSession));
        when(monitorHelper.getLatestActiveOrderByDirection(twoPositionsSession, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.getLatestActiveOrderByDirection(twoPositionsSession, TradingDirection.SHORT)).thenReturn(shortOrder);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(monitorHelper.determineCloseOrderPurpose(shortOrder)).thenReturn(OrderPurpose.HEDGE_CLOSE);
        when(tradingUpdatesService.closePosition(
                any(TradeSession.class), any(SessionMode.class), eq(1001L), 
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE), 
                isNull(), eq("api_close_all_long")
        )).thenReturn(twoPositionsSession);
        when(tradingUpdatesService.closePosition(
                any(TradeSession.class), any(SessionMode.class), eq(1002L), 
                isNull(), eq(TradingDirection.SHORT), eq(OrderPurpose.HEDGE_CLOSE), 
                isNull(), eq("api_close_all_short")
        )).thenReturn(completedSession);
        when(modelMapper.map(completedSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeAllActiveOrders(sessionId);

        // Then
        assertNotNull(result);

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(monitoringService).removeFromMonitoring(sessionId);
        verify(monitorHelper).getLatestActiveOrderByDirection(twoPositionsSession, TradingDirection.LONG);
        verify(monitorHelper).getLatestActiveOrderByDirection(twoPositionsSession, TradingDirection.SHORT);
        verify(monitorHelper).determineCloseOrderPurpose(longOrder);
        verify(monitorHelper).determineCloseOrderPurpose(shortOrder);
        verify(tradingUpdatesService).closePosition(
                any(TradeSession.class), any(SessionMode.class), eq(1001L), 
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE), 
                isNull(), eq("api_close_all_long")
        );
        verify(tradingUpdatesService).closePosition(
                any(TradeSession.class), any(SessionMode.class), eq(1002L), 
                isNull(), eq(TradingDirection.SHORT), eq(OrderPurpose.HEDGE_CLOSE), 
                isNull(), eq("api_close_all_short")
        );
        verify(modelMapper).map(completedSession, SessionDto.class);
    }

    @Test
    @DisplayName("Should close all active orders - only LONG active")
    void testCloseAllActiveOrdersOnlyLong() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(monitorHelper.getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(tradingUpdatesService.closePosition(
                eq(activeSession), eq(SessionMode.SCALPING), eq(1001L), 
                isNull(), eq(TradingDirection.LONG), eq(OrderPurpose.MAIN_CLOSE), 
                isNull(), eq("api_close_all_long")
        )).thenReturn(completedSession);
        when(modelMapper.map(completedSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeAllActiveOrders(sessionId);

        // Then
        assertNotNull(result);

        // Verify что SHORT не закрывался
        verify(monitorHelper, never()).getLatestActiveOrderByDirection(activeSession, TradingDirection.SHORT);
        verify(tradingUpdatesService, never()).closePosition(
                any(), any(), eq(1002L), any(), any(), any(), any(), eq("api_close_all_short")
        );
    }

    @Test
    @DisplayName("Should close all active orders - no active positions")
    void testCloseAllActiveOrdersNoActive() {
        // Given
        // Создаем сессию без активных позиций
        TradeSession noActiveSession = TradeSession.builder()
                .id(sessionId)
                .tradePlan(planId)
                .status(SessionStatus.ACTIVE)
                .activeLong(false)
                .activeShort(false)
                .orders(new ArrayList<>())
                .build();

        when(repository.findById(sessionId)).thenReturn(Optional.of(noActiveSession));
        when(modelMapper.map(noActiveSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeAllActiveOrders(sessionId);

        // Then
        assertNotNull(result);

        // Verify что ничего не закрывалось
        verify(monitorHelper, never()).getLatestActiveOrderByDirection(any(), any());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should close session successfully")
    void testCloseSession() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(modelMapper.map(activeSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeSession(sessionId);

        // Then
        assertNotNull(result);

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(monitoringService).removeFromMonitoring(sessionId);
        verify(tradePlanUpdateService).setActiveFalse(planId);
        verify(tradePlanUpdateService).addProfit(eq(planId), any(BigDecimal.class));
        verify(repository).save(any(TradeSession.class));
        verify(modelMapper).map(any(TradeSession.class), eq(SessionDto.class));
    }

    @Test
    @DisplayName("Should throw exception when session not found")
    void testSessionNotFound() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(TradeSessionNotFoundException.class,
                () -> sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.LONG));

        assertThrows(TradeSessionNotFoundException.class,
                () -> sessionHandleActionsService.closeAllActiveOrders(sessionId));

        assertThrows(TradeSessionNotFoundException.class,
                () -> sessionHandleActionsService.closeSession(sessionId));

        // Verify interactions
        verify(repository, times(3)).findById(sessionId);
        verify(monitoringService, never()).removeFromMonitoring(anyString());
        verify(tradingUpdatesService, never()).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should handle null response from tradingUpdatesService")
    void testNullResponseFromTradingUpdates() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(monitorHelper.isDirectionActive(activeSession, TradingDirection.LONG)).thenReturn(true);
        when(monitorHelper.getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        when(modelMapper.map(activeSession, SessionDto.class)).thenReturn(sessionDto);

        // When
        SessionDto result = sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.LONG);

        // Then
        assertNotNull(result);

        // Verify что использована оригинальная сессия при null ответе
        verify(modelMapper).map(activeSession, SessionDto.class);
    }

    @Test
    @DisplayName("Should handle tradingUpdatesService exceptions gracefully")
    void testTradingUpdatesServiceException() {
        // Given
        when(repository.findById(sessionId)).thenReturn(Optional.of(activeSession));
        when(monitorHelper.isDirectionActive(activeSession, TradingDirection.LONG)).thenReturn(true);
        when(monitorHelper.getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG)).thenReturn(longOrder);
        when(monitorHelper.determineCloseOrderPurpose(longOrder)).thenReturn(OrderPurpose.MAIN_CLOSE);
        when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Trading service error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> 
            sessionHandleActionsService.closeOrderByDirection(sessionId, TradingDirection.LONG)
        );

        // Verify interactions
        verify(repository).findById(sessionId);
        verify(monitoringService).removeFromMonitoring(sessionId);
        verify(monitorHelper).isDirectionActive(activeSession, TradingDirection.LONG);
        verify(monitorHelper).getLatestActiveOrderByDirection(activeSession, TradingDirection.LONG);
        verify(monitorHelper).determineCloseOrderPurpose(longOrder);
        verify(tradingUpdatesService).closePosition(any(), any(), any(), any(), any(), any(), any(), any());
    }
}

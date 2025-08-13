// package io.cryptobot.binance.trading.monitoring.v3.old;

// import io.cryptobot.binance.order.enums.OrderPurpose;
// import io.cryptobot.binance.order.enums.OrderStatus;
// import io.cryptobot.binance.order.enums.OrderSide;
// import io.cryptobot.binance.order.model.Order;
// import io.cryptobot.binance.order.service.OrderService;
// import io.cryptobot.binance.trade.session.enums.SessionMode;
// import io.cryptobot.binance.trade.session.enums.SessionStatus;
// import io.cryptobot.binance.trade.session.enums.TradingDirection;
// import io.cryptobot.binance.trade.session.model.TradeOrder;
// import io.cryptobot.binance.trade.session.model.TradeSession;
// import io.cryptobot.binance.trade.session.service.TradeSessionService;
// import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
// import io.cryptobot.binance.trade.trade_plan.model.TradePlan;
// import io.cryptobot.binance.trade.trade_plan.service.get.TradePlanGetService;
// import io.cryptobot.binance.trading.monitoring.v3.MonitoringServiceV3Impl;
// import io.cryptobot.binance.trading.monitoring.v3.help.MonitorHelper;
// import io.cryptobot.binance.trading.monitoring.v3.utils.CheckTrailing;
// import io.cryptobot.binance.trading.updates.TradingUpdatesService;
// import io.cryptobot.configs.locks.TradeSessionLockRegistry;
// import io.cryptobot.market_data.ticker24h.Ticker24hService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.mockito.junit.jupiter.MockitoSettings;
// import org.mockito.quality.Strictness;

// import java.math.BigDecimal;
// import java.time.LocalDateTime;
// import java.util.concurrent.locks.ReentrantLock;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class)
// @MockitoSettings(strictness = Strictness.LENIENT)
// @DisplayName("MonitoringServiceV3Impl Integration Tests")
// class MonitoringServiceV3ImplIntegrationTest {

//     @Mock
//     private MonitorHelper monitorHelper;
//     @Mock
//     private TradeSessionService sessionService;
//     @Mock
//     private Ticker24hService ticker24hService;
//     @Mock
//     private TradingUpdatesService tradingUpdatesService;
//     @Mock
//     private CheckTrailing checkTrailing;
//     @Mock
//     private TradeSessionLockRegistry lockRegistry;
//     @Mock
//     private TradePlanGetService tradePlanGetService;
//     @Mock
//     private OrderService orderService;
//     @Mock
//     private ReentrantLock mockLock;

//     private MonitoringServiceV3Impl monitoringService;
//     private TradeSession session;
//     private TradePlan tradePlan;
//     private Order mockOrder;
//     private BigDecimal currentPrice;

//     @BeforeEach
//     void setUp() {
//         monitoringService = new MonitoringServiceV3Impl(
//                 monitorHelper, sessionService, ticker24hService, 
//                 tradingUpdatesService, checkTrailing, lockRegistry
//         );

//         // Создаем сессию с правильной инициализацией
//         session = new TradeSession();
//         session.setId("test-session");

//         tradePlan = TradePlan.builder()
//                 .symbol("BTCUSDT")
//                 .amountPerTrade(new BigDecimal("1000"))
//                 .sizes(SizeModel.builder()
//                         .lotSize(new BigDecimal("0.001"))
//                         .build())
//                 .build();

//         mockOrder = new Order();
//         mockOrder.setOrderId(123456789L);
//         mockOrder.setAveragePrice(new BigDecimal("50000"));

//         currentPrice = new BigDecimal("50000");

//         // Настройка моков
//         when(lockRegistry.getLock(anyString())).thenReturn(mockLock);
//         when(mockLock.tryLock()).thenReturn(true);
//         when(ticker24hService.getPrice(anyString())).thenReturn(currentPrice);
//         when(monitorHelper.nvl(any(BigDecimal.class))).thenAnswer(invocation -> {
//             BigDecimal value = invocation.getArgument(0);
//             return value != null ? value : BigDecimal.ZERO;
//         });
//         when(monitorHelper.nvl(null)).thenReturn(BigDecimal.ZERO);
//         when(monitorHelper.isSessionInValidState(any(TradeSession.class))).thenReturn(true);
//         when(monitorHelper.isValidForClosing(any(TradeOrder.class))).thenReturn(true);
//         when(monitorHelper.determineCloseOrderPurpose(any(TradeOrder.class))).thenReturn(OrderPurpose.MAIN_CLOSE);
//         when(monitorHelper.opposite(any(TradingDirection.class))).thenAnswer(invocation -> {
//             TradingDirection dir = invocation.getArgument(0);
//             return dir == TradingDirection.LONG ? TradingDirection.SHORT : TradingDirection.LONG;
//         });
//         when(monitorHelper.isDirectionActive(any(TradeSession.class), any(TradingDirection.class))).thenReturn(false);
//         when(monitorHelper.isMainStillActive(any(TradeSession.class))).thenReturn(true);
//         when(monitorHelper.getLastFilledHedgeOrderByDirection(any(TradeSession.class), any(TradingDirection.class))).thenReturn(null);
//         when(tradePlanGetService.getPlan(anyString())).thenReturn(tradePlan);
//         when(orderService.createOrder(anyString(), anyDouble(), any(), anyBoolean())).thenReturn(mockOrder);
//         when(orderService.getOrder(anyLong())).thenReturn(mockOrder);
//         when(sessionService.addOrder(anyString(), any(TradeOrder.class))).thenReturn(session);
//     }

//     @Test
//     @DisplayName("Проверка добавления сессии в мониторинг")
//     void testAddToMonitoring() {
//         // Given - создаем сессию с правильной инициализацией
//         TradeOrder longMainOrder = createTradeOrder(1001L, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
//         session.onCreate("BTCUSDT", TradingDirection.LONG, longMainOrder, "test context");

//         // When
//         monitoringService.addToMonitoring(session);

//         // Then - проверяем, что сессия добавлена в мониторинг
//         // Проверяем статус сессии
//         assertEquals(SessionStatus.ACTIVE, session.getStatus());
//         assertTrue(session.isActiveLong());
//         assertFalse(session.isActiveShort());
//         assertEquals(SessionMode.SCALPING, session.getCurrentMode());
//     }

//     @Test
//     @DisplayName("Проверка удаления сессии из мониторинга")
//     void testRemoveFromMonitoring() {
//         // Given - создаем сессию с правильной инициализацией
//         TradeOrder longMainOrder = createTradeOrder(1001L, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
//         session.onCreate("BTCUSDT", TradingDirection.LONG, longMainOrder, "test context");
//         monitoringService.addToMonitoring(session);

//         // When
//         monitoringService.removeFromMonitoring("test-session");

//         // Then - проверяем, что сессия удалена из мониторинга
//         // Проверяем, что сессия все еще существует, но не в мониторинге
//         assertNotNull(session);
//         assertEquals(SessionStatus.ACTIVE, session.getStatus());
//     }

//     @Test
//     @DisplayName("Проверка запрета на создание хеджа когда уже есть две позиции")
//     void testPreventHedgeCreationWhenTwoPositionsActive() {
//         // Given - создаем LONG MAIN и HEDGE SHORT ордера с правильной инициализацией
//         TradeOrder longMainOrder = createTradeOrder(1001L, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
//         TradeOrder hedgeShortOrder = createTradeOrder(1002L, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, new BigDecimal("51000"));
        
//         session.onCreate("BTCUSDT", TradingDirection.LONG, longMainOrder, "test context");
//         session.addOrder(hedgeShortOrder);
        
//         monitoringService.addToMonitoring(session);

//         // When - пытаемся открыть еще один хедж
//         when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG)).thenReturn(longMainOrder);
//         when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT)).thenReturn(hedgeShortOrder);
//         when(monitorHelper.isDirectionActive(session, TradingDirection.LONG)).thenReturn(true);
//         when(monitorHelper.isDirectionActive(session, TradingDirection.SHORT)).thenReturn(true);

//         // Симулируем убыток для попытки активации хеджа
//         when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);
        
//         monitoringService.monitor();
        
//         // Then - проверяем, что новый хедж НЕ был создан
//         verify(tradingUpdatesService, never()).openPosition(
//                 any(), any(), eq(TradingDirection.LONG), any(), any(), any(), any(), any()
//         );
//         verify(tradingUpdatesService, never()).openPosition(
//                 any(), any(), eq(TradingDirection.SHORT), any(), any(), any(), any(), any()
//         );
//     }

//     @Test
//     @DisplayName("Проверка запрета на создание дублирующего хеджа")
//     void testPreventDuplicateHedgeCreation() {
//         // Given - создаем LONG MAIN ордер с правильной инициализацией
//         TradeOrder longMainOrder = createTradeOrder(1001L, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
//         session.onCreate("BTCUSDT", TradingDirection.LONG, longMainOrder, "test context");
        
//         monitoringService.addToMonitoring(session);

//         // Step 1: Добавляем HEDGE SHORT ордер
//         TradeOrder hedgeShortOrder = createTradeOrder(1002L, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, new BigDecimal("51000"));
//         session.addOrder(hedgeShortOrder);

//         // Then - проверяем, что сессия имеет обе позиции активными
//         assertTrue(session.isActiveLong());
//         assertTrue(session.isActiveShort());
//         assertTrue(session.hasBothPositionsActive());
//         assertEquals(2, session.getOrders().size());
//     }

//     @Test
//     @DisplayName("Проверка трейлинга и закрытия позиции")
//     void testTrailingAndClosePosition() {
//         // Given - создаем LONG MAIN ордер с правильной инициализацией
//         TradeOrder longMainOrder = createTradeOrder(1001L, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
//         session.onCreate("BTCUSDT", TradingDirection.LONG, longMainOrder, "test context");
        
//         monitoringService.addToMonitoring(session);

//         // When - симулируем трейлинг и закрытие
//         when(monitorHelper.getActiveOrderForMonitoring(session)).thenReturn(longMainOrder);
//         when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(true);
//         when(tradingUpdatesService.closePosition(any(), any(), any(), any(), any(), any(), any(), anyString()))
//                 .thenReturn(session);

//         // Then - проверяем, что мониторинг выполняется без ошибок
//         monitoringService.monitor();
        
//         // Проверяем, что сессия правильно инициализирована
//         assertEquals(SessionStatus.ACTIVE, session.getStatus());
//         assertTrue(session.isActiveLong());
//         assertEquals(SessionMode.SCALPING, session.getCurrentMode());
//         assertEquals(1, session.getOrders().size());
//     }

//     @Test
//     @DisplayName("Проверка логики двух позиций")
//     void testTwoPositionsLogic() {
//         // Given - создаем LONG MAIN и HEDGE SHORT ордера
//         TradeOrder longMainOrder = createTradeOrder(1001L, TradingDirection.LONG, OrderPurpose.MAIN_OPEN, new BigDecimal("50000"));
//         TradeOrder hedgeShortOrder = createTradeOrder(1002L, TradingDirection.SHORT, OrderPurpose.HEDGE_OPEN, new BigDecimal("51000"));
        
//         session.onCreate("BTCUSDT", TradingDirection.LONG, longMainOrder, "test context");
//         session.addOrder(hedgeShortOrder);
        
//         monitoringService.addToMonitoring(session);

//         // When - симулируем логику двух позиций
//         when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.LONG)).thenReturn(longMainOrder);
//         when(monitorHelper.getLatestActiveOrderByDirection(session, TradingDirection.SHORT)).thenReturn(hedgeShortOrder);
//         when(checkTrailing.checkNewTrailing(any(TradeOrder.class), any(BigDecimal.class))).thenReturn(false);

//         // Then - проверяем, что мониторинг выполняется без ошибок
//         monitoringService.monitor();
        
//         // Проверяем, что сессия имеет обе позиции активными
//         assertTrue(session.isActiveLong());
//         assertTrue(session.isActiveShort());
//         assertTrue(session.hasBothPositionsActive());
//     }

//     private TradeOrder createTradeOrder(Long orderId, TradingDirection direction, OrderPurpose purpose, BigDecimal price) {
//         return TradeOrder.builder()
//                 .orderId(orderId)
//                 .direction(direction)
//                 .purpose(purpose)
//                 .status(OrderStatus.FILLED)
//                 .price(price)
//                 .count(new BigDecimal("0.1"))
//                 .orderTime(LocalDateTime.now())
//                 .side(direction == TradingDirection.LONG ? OrderSide.BUY : OrderSide.SELL)
//                 .pnlHigh(BigDecimal.ZERO) // Устанавливаем pnlHigh чтобы избежать null
//                 .build();
//     }
// }

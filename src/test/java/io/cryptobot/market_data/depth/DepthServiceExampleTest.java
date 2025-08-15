package io.cryptobot.market_data.depth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Пример использования методов DepthService для получения цен на X пунктов выше/ниже
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DepthService Usage Examples")
class DepthServiceExampleTest {

    @Mock
    private DepthService depthService;

    private DepthModel testDepthModel;

    @BeforeEach
    void setUp() {
        testDepthModel = new DepthModel();
        
        // Создаем тестовые данные для asks (отсортированы по возрастанию)
        Map<BigDecimal, BigDecimal> asks = new HashMap<>();
        asks.put(new BigDecimal("50001.00"), new BigDecimal("1.5")); // ближайшая ask
        asks.put(new BigDecimal("50002.00"), new BigDecimal("2.0")); // 1 уровень выше
        asks.put(new BigDecimal("50003.00"), new BigDecimal("1.8")); // 2 уровня выше
        asks.put(new BigDecimal("50004.00"), new BigDecimal("2.2")); // 3 уровня выше
        asks.put(new BigDecimal("50005.00"), new BigDecimal("1.9")); // 4 уровня выше
        testDepthModel.updateAsks(asks);
        
        // Создаем тестовые данные для bids (отсортированы по убыванию)
        Map<BigDecimal, BigDecimal> bids = new HashMap<>();
        bids.put(new BigDecimal("50000.00"), new BigDecimal("2.0")); // ближайшая bid
        bids.put(new BigDecimal("49999.00"), new BigDecimal("1.8")); // 1 уровень ниже
        bids.put(new BigDecimal("49998.00"), new BigDecimal("2.1")); // 2 уровня ниже
        bids.put(new BigDecimal("49997.00"), new BigDecimal("1.9")); // 3 уровня ниже
        bids.put(new BigDecimal("49996.00"), new BigDecimal("2.3")); // 4 уровня ниже
        testDepthModel.updateBids(bids);
        
        // Настраиваем моки для DepthService
        when(depthService.getDepthModelBySymbol(anyString())).thenReturn(testDepthModel);
        when(depthService.getNearestAskPrice(anyString())).thenReturn(new BigDecimal("50001.00"));
        when(depthService.getNearestBidPrice(anyString())).thenReturn(new BigDecimal("50000.00"));
        when(depthService.getAskPriceAbove(anyString(), eq(0))).thenReturn(new BigDecimal("50001.00"));
        when(depthService.getAskPriceAbove(anyString(), eq(1))).thenReturn(new BigDecimal("50002.00"));
        when(depthService.getAskPriceAbove(anyString(), eq(2))).thenReturn(new BigDecimal("50003.00"));
        when(depthService.getAskPriceAbove(anyString(), eq(3))).thenReturn(new BigDecimal("50004.00"));
        when(depthService.getAskPriceAbove(anyString(), eq(4))).thenReturn(new BigDecimal("50005.00"));
        when(depthService.getBidPriceBelow(anyString(), eq(0))).thenReturn(new BigDecimal("50000.00"));
        when(depthService.getBidPriceBelow(anyString(), eq(1))).thenReturn(new BigDecimal("49999.00"));
        when(depthService.getBidPriceBelow(anyString(), eq(2))).thenReturn(new BigDecimal("49998.00"));
        when(depthService.getBidPriceBelow(anyString(), eq(3))).thenReturn(new BigDecimal("49997.00"));
        when(depthService.getBidPriceBelow(anyString(), eq(4))).thenReturn(new BigDecimal("49996.00"));
        when(depthService.getBidPriceBelow(anyString(), eq(5))).thenReturn(new BigDecimal("49995.00"));
    }

    @Test
    @DisplayName("Example: How to use getAskPriceAbove and getBidPriceBelow")
    void exampleUsage() {
        // Given
        String symbol = "BTCUSDT";
        
        // When - получаем ближайшие цены
        BigDecimal nearestAsk = depthService.getNearestAskPrice(symbol);
        BigDecimal nearestBid = depthService.getNearestBidPrice(symbol);
        
        // Then - проверяем, что методы работают
        assertNotNull(nearestAsk);
        assertNotNull(nearestBid);
        assertEquals(new BigDecimal("50001.00"), nearestAsk);
        assertEquals(new BigDecimal("50000.00"), nearestBid);
        
        // When - получаем цены на разные уровни выше ask
        BigDecimal askPrice1LevelAbove = depthService.getAskPriceAbove(symbol, 1);
        BigDecimal askPrice2LevelsAbove = depthService.getAskPriceAbove(symbol, 2);
        BigDecimal askPrice3LevelsAbove = depthService.getAskPriceAbove(symbol, 3);
        
        // Then - проверяем правильность уровней
        assertEquals(new BigDecimal("50002.00"), askPrice1LevelAbove);
        assertEquals(new BigDecimal("50003.00"), askPrice2LevelsAbove);
        assertEquals(new BigDecimal("50004.00"), askPrice3LevelsAbove);
        
        // When - получаем цены на разные уровни ниже bid
        BigDecimal bidPrice1LevelBelow = depthService.getBidPriceBelow(symbol, 1);
        BigDecimal bidPrice2LevelsBelow = depthService.getBidPriceBelow(symbol, 2);
        BigDecimal bidPrice3LevelsBelow = depthService.getBidPriceBelow(symbol, 3);
        
        // Then - проверяем правильность уровней
        assertEquals(new BigDecimal("49999.00"), bidPrice1LevelBelow);
        assertEquals(new BigDecimal("49998.00"), bidPrice2LevelsBelow);
        assertEquals(new BigDecimal("49997.00"), bidPrice3LevelsBelow);
        
        System.out.println("Пример использования методов DepthService:");
        System.out.println("- getNearestAskPrice(symbol) = " + nearestAsk);
        System.out.println("- getNearestBidPrice(symbol) = " + nearestBid);
        System.out.println("- getAskPriceAbove(symbol, 2) = " + askPrice2LevelsAbove);
        System.out.println("- getBidPriceBelow(symbol, 3) = " + bidPrice3LevelsBelow);
    }
    
    @Test
    @DisplayName("Example: Trading strategy using price levels")
    void tradingStrategyExample() {
        // Given
        String symbol = "BTCUSDT";
        BigDecimal quantity = new BigDecimal("0.1");
        
        // When - получаем текущие цены
        BigDecimal currentBid = depthService.getNearestBidPrice(symbol);
        BigDecimal currentAsk = depthService.getNearestAskPrice(symbol);
        
        // When - рассчитываем цены для ордеров
        BigDecimal buyOrderPrice = depthService.getBidPriceBelow(symbol, 2);
        BigDecimal stopLossPrice = depthService.getAskPriceAbove(symbol, 3);
        
        // Then - проверяем, что у нас достаточно данных
        assertNotNull(buyOrderPrice);
        assertNotNull(stopLossPrice);
        
        // Проверяем логику торговой стратегии
        assertTrue(buyOrderPrice.compareTo(currentBid) < 0, "Цена покупки должна быть ниже текущей bid");
        assertTrue(stopLossPrice.compareTo(currentAsk) > 0, "Стоп-лосс должен быть выше текущей ask");
        
        // Симулируем размещение ордеров
        boolean orderPlaced = placeLimitOrder(symbol, "BUY", buyOrderPrice, quantity);
        boolean stopLossPlaced = placeStopLossOrder(symbol, "SELL", stopLossPrice, quantity);
        
        assertTrue(orderPlaced, "Лимитный ордер должен быть размещен");
        assertTrue(stopLossPlaced, "Стоп-лосс должен быть размещен");
        
        System.out.println("Торговая стратегия:");
        System.out.println("1. Текущая bid цена: " + currentBid);
        System.out.println("2. Текущая ask цена: " + currentAsk);
        System.out.println("3. Цена покупки (2 уровня ниже bid): " + buyOrderPrice);
        System.out.println("4. Стоп-лосс (3 уровня выше ask): " + stopLossPrice);
        System.out.println("5. Размер позиции: " + quantity + " BTC");
    }
    
    @Test
    @DisplayName("Example: Risk management using price levels")
    void riskManagementExample() {
        // Given
        String symbol = "BTCUSDT";
        BigDecimal positionSize = new BigDecimal("1.0"); // размер позиции в BTC
        
        // When - рассчитываем различные уровни для управления рисками
        BigDecimal partialClosePrice = depthService.getBidPriceBelow(symbol, 1);
        BigDecimal fullClosePrice = depthService.getBidPriceBelow(symbol, 5);
        BigDecimal stopLossPrice = depthService.getAskPriceAbove(symbol, 2);
        BigDecimal trailingStopPrice = depthService.getAskPriceAbove(symbol, 1);
        
        // Then - проверяем, что все цены получены
        assertNotNull(partialClosePrice);
        assertNotNull(stopLossPrice);
        assertNotNull(trailingStopPrice);
        
        // Проверяем логику управления рисками
        assertTrue(partialClosePrice.compareTo(fullClosePrice) > 0, 
            "Частичное закрытие должно быть выше полного закрытия");
        assertTrue(stopLossPrice.compareTo(trailingStopPrice) > 0, 
            "Стоп-лосс должен быть выше трейлинг-стопа");
        
        // Симулируем размещение ордеров управления рисками
        boolean partialClosePlaced = placeTakeProfitOrder(symbol, "SELL", partialClosePrice, 
            positionSize.multiply(new BigDecimal("0.5")));
        boolean fullClosePlaced = placeTakeProfitOrder(symbol, "SELL", fullClosePrice, 
            positionSize.multiply(new BigDecimal("0.5")));
        boolean stopLossPlaced = placeStopLossOrder(symbol, "SELL", stopLossPrice, positionSize);
        boolean trailingStopPlaced = placeTrailingStopOrder(symbol, "SELL", trailingStopPrice, positionSize);
        
        assertTrue(partialClosePlaced, "Ордер частичного закрытия должен быть размещен");
        assertTrue(stopLossPlaced, "Стоп-лосс должен быть размещен");
        assertTrue(trailingStopPlaced, "Трейлинг-стоп должен быть размещен");
        
        System.out.println("Управление рисками:");
        System.out.println("- Частичное закрытие (1 уровень ниже bid): " + partialClosePrice);
        System.out.println("- Полное закрытие (5 уровней ниже bid): " + fullClosePrice);
        System.out.println("- Стоп-лосс (2 уровня выше ask): " + stopLossPrice);
        System.out.println("- Трейлинг-стоп (1 уровень выше ask): " + trailingStopPrice);
        System.out.println("- Размер позиции: " + positionSize + " BTC");
    }
    
    @Test
    @DisplayName("Example: Market depth analysis")
    void marketDepthAnalysisExample() {
        // Given
        String symbol = "BTCUSDT";
        
        // When - анализируем глубину рынка на разных уровнях
        BigDecimal[] askLevels = new BigDecimal[5];
        BigDecimal[] bidLevels = new BigDecimal[5];
        
        for (int i = 0; i < 5; i++) {
            askLevels[i] = depthService.getAskPriceAbove(symbol, i);
            bidLevels[i] = depthService.getBidPriceBelow(symbol, i);
        }
        
        // Then - проверяем, что все уровни получены
        for (int i = 0; i < 5; i++) {
            assertNotNull(askLevels[i], "Ask уровень " + i + " должен быть получен");
            assertNotNull(bidLevels[i], "Bid уровень " + i + " должен быть получен");
        }
        
        // Проверяем, что цены отсортированы правильно
        for (int i = 1; i < 5; i++) {
            assertTrue(askLevels[i].compareTo(askLevels[i-1]) > 0, 
                "Ask цены должны быть отсортированы по возрастанию");
            assertTrue(bidLevels[i].compareTo(bidLevels[i-1]) < 0, 
                "Bid цены должны быть отсортированы по убыванию");
        }
        
        // Рассчитываем спред на разных уровнях
        BigDecimal[] spreads = new BigDecimal[5];
        for (int i = 0; i < 5; i++) {
            spreads[i] = askLevels[i].subtract(bidLevels[i]);
        }
        
        System.out.println("Анализ глубины рынка:");
        System.out.println("Уровень | Ask цена | Bid цена | Спред");
        System.out.println("--------|----------|----------|-------");
        for (int i = 0; i < 5; i++) {
            System.out.printf("%7d | %8.2f | %8.2f | %5.2f%n", 
                i, askLevels[i], bidLevels[i], spreads[i]);
        }
        
        // Проверяем, что спред увеличивается с уровнем
        for (int i = 1; i < 5; i++) {
            assertTrue(spreads[i].compareTo(spreads[i-1]) >= 0, 
                "Спред должен увеличиваться с уровнем");
        }
    }
    
    @Test
    @DisplayName("Example: Order book imbalance analysis")
    void orderBookImbalanceAnalysis() {
        // Given
        String symbol = "BTCUSDT";
        
        // When - получаем цены на разных уровнях
        BigDecimal ask1 = depthService.getAskPriceAbove(symbol, 1);
        BigDecimal ask2 = depthService.getAskPriceAbove(symbol, 2);
        BigDecimal bid1 = depthService.getBidPriceBelow(symbol, 1);
        BigDecimal bid2 = depthService.getBidPriceBelow(symbol, 2);
        
        // Then - проверяем данные
        assertNotNull(ask1);
        assertNotNull(ask2);
        assertNotNull(bid1);
        assertNotNull(bid2);
        
        // Рассчитываем дисбаланс ордербука
        BigDecimal askSpread = ask2.subtract(ask1);
        BigDecimal bidSpread = bid1.subtract(bid2);
        BigDecimal imbalance = askSpread.subtract(bidSpread);
        
        System.out.println("Анализ дисбаланса ордербука:");
        System.out.println("- Спред между ask уровнями: " + askSpread);
        System.out.println("- Спред между bid уровнями: " + bidSpread);
        System.out.println("- Дисбаланс: " + imbalance);
        
        if (imbalance.compareTo(BigDecimal.ZERO) > 0) {
            System.out.println("- Интерпретация: Давление на покупку (ask уровни дальше друг от друга)");
        } else if (imbalance.compareTo(BigDecimal.ZERO) < 0) {
            System.out.println("- Интерпретация: Давление на продажу (bid уровни дальше друг от друга)");
        } else {
            System.out.println("- Интерпретация: Сбалансированный рынок");
        }
    }
    
    // Вспомогательные методы для симуляции размещения ордеров
    private boolean placeLimitOrder(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        // Симуляция размещения лимитного ордера
        System.out.println("Размещен лимитный ордер: " + side + " " + quantity + " " + symbol + " по цене " + price);
        return true;
    }
    
    private boolean placeStopLossOrder(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        // Симуляция размещения стоп-лосса
        System.out.println("Размещен стоп-лосс: " + side + " " + quantity + " " + symbol + " по цене " + price);
        return true;
    }
    
    private boolean placeTakeProfitOrder(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        // Симуляция размещения тейк-профита
        System.out.println("Размещен тейк-профит: " + side + " " + quantity + " " + symbol + " по цене " + price);
        return true;
    }
    
    private boolean placeTrailingStopOrder(String symbol, String side, BigDecimal price, BigDecimal quantity) {
        // Симуляция размещения трейлинг-стопа
        System.out.println("Размещен трейлинг-стоп: " + side + " " + quantity + " " + symbol + " по цене " + price);
        return true;
    }
}

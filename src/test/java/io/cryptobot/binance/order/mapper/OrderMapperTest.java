package io.cryptobot.binance.order.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.binance.order.enums.OrderSide;
import io.cryptobot.binance.order.enums.OrderStatus;
import io.cryptobot.binance.order.enums.OrderType;
import io.cryptobot.binance.order.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderMapper Tests")
class OrderMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should map REST order correctly")
    void testFromRest() throws Exception {
        // Given
        String json = """
                {
                    "symbol": "BTCUSDT",
                    "clientOrderId": "client123",
                    "side": "BUY",
                    "type": "LIMIT",
                    "timeInForce": "GTC",
                    "origQty": "0.001",
                    "price": "50000.00",
                    "avgPrice": "50000.50",
                    "executedQty": "0.001",
                    "status": "FILLED",
                    "orderId": 123456789,
                    "positionSide": "LONG",
                    "reduceOnly": false,
                    "origType": "LIMIT",
                    "workingType": "CONTRACT_PRICE",
                    "selfTradePreventionMode": "NONE"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromRest(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertEquals("client123", order.getClientOrderId());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals("GTC", order.getTimeInForce());
        assertEquals(new BigDecimal("0.001"), order.getQuantity());
        assertEquals(new BigDecimal("50000.00"), order.getPrice());
        assertEquals(new BigDecimal("50000.50"), order.getAveragePrice());
        assertEquals(new BigDecimal("0.001"), order.getCumulativeFilledQty());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
        assertEquals(123456789L, order.getOrderId());
        assertEquals("LONG", order.getPositionSide());
        assertFalse(order.isReduceOnly());
        assertEquals("LIMIT", order.getOriginalType());
        assertEquals("CONTRACT_PRICE", order.getWorkingType());
        assertEquals("NONE", order.getOriginalResponseType());
    }

    @Test
    @DisplayName("Should map WebSocket order correctly")
    void testFromWS() throws Exception {
        // Given
        String json = """
                {
                    "s": "BTCUSDT",
                    "c": "client123",
                    "S": "SELL",
                    "o": "MARKET",
                    "f": "IOC",
                    "q": "0.002",
                    "p": "49000.00",
                    "ap": "49000.25",
                    "sp": "48500.00",
                    "x": "TRADE",
                    "X": "FILLED",
                    "i": 987654321,
                    "l": "0.001",
                    "z": "0.001",
                    "L": "49000.25",
                    "n": "0.0001",
                    "N": "BTC",
                    "T": 1640995200000,
                    "t": 12345,
                    "m": true,
                    "R": true,
                    "wt": "MARK_PRICE",
                    "ot": "MARKET",
                    "ps": "SHORT",
                    "cp": false,
                    "rp": "10.50",
                    "pP": true,
                    "si": 1,
                    "ss": 0,
                    "V": "NONE",
                    "pm": "HEDGE_MODE",
                    "gtd": 1640998800000
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromWS(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertEquals("client123", order.getClientOrderId());
        assertEquals(OrderSide.SELL, order.getSide());
        assertEquals(OrderType.MARKET, order.getOrderType());
        assertEquals("IOC", order.getTimeInForce());
        assertEquals(new BigDecimal("0.002"), order.getQuantity());
        assertEquals(new BigDecimal("49000.00"), order.getPrice());
        assertEquals(new BigDecimal("49000.25"), order.getAveragePrice());
        assertEquals(new BigDecimal("48500.00"), order.getStopPrice());
        assertEquals("TRADE", order.getExecutionType());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
        assertEquals(987654321L, order.getOrderId());
        assertEquals(new BigDecimal("0.001"), order.getLastFilledQty());
        assertEquals(new BigDecimal("0.001"), order.getCumulativeFilledQty());
        assertEquals(new BigDecimal("49000.25"), order.getLastFilledPrice());
        assertEquals(new BigDecimal("0.0001"), order.getCommission());
        assertEquals("BTC", order.getCommissionAsset());
        assertEquals(1640995200000L, order.getTradeTime());
        assertEquals(12345L, order.getTradeId());
        assertTrue(order.isBuyerIsMaker());
        assertTrue(order.isReduceOnly());
        assertEquals("MARK_PRICE", order.getWorkingType());
        assertEquals("MARKET", order.getOriginalType());
        assertEquals("SHORT", order.getPositionSide());
        assertFalse(order.isClosePosition());
        assertEquals(new BigDecimal("10.50"), order.getRealizedPnl());
        assertTrue(order.isPositionPnl());
        assertEquals(1, order.getSideEffectType());
        assertEquals(0, order.getStopStatus());
        assertEquals("NONE", order.getOriginalResponseType());
        assertEquals("HEDGE_MODE", order.getPositionMode());
        assertEquals(1640998800000L, order.getGoodTillDate());
    }

    @Test
    @DisplayName("Should handle null REST node")
    void testFromRestNull() {
        // When
        Order order = OrderMapper.fromRest(null);

        // Then
        assertNull(order);
    }

    @Test
    @DisplayName("Should handle null WebSocket node")
    void testFromWSNull() {
        // When
        Order order = OrderMapper.fromWS(null);

        // Then
        assertNull(order);
    }

    @Test
    @DisplayName("Should handle invalid JSON in REST")
    void testFromRestInvalidJson() throws Exception {
        // Given
        String json = """
                {
                    "symbol": "BTCUSDT",
                    "side": "INVALID_SIDE",
                    "status": "INVALID_STATUS"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromRest(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertNull(order.getSide()); // Invalid side should be null
        assertNull(order.getOrderStatus()); // Invalid status should be null
    }

    @Test
    @DisplayName("Should handle invalid JSON in WebSocket")
    void testFromWSInvalidJson() throws Exception {
        // Given
        String json = """
                {
                    "s": "BTCUSDT",
                    "S": "INVALID_SIDE",
                    "X": "INVALID_STATUS"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromWS(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertNull(order.getSide()); // Invalid side should be null
        assertNull(order.getOrderStatus()); // Invalid status should be null
    }

    @Test
    @DisplayName("Should handle missing fields in REST")
    void testFromRestMissingFields() throws Exception {
        // Given
        String json = """
                {
                    "symbol": "BTCUSDT"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromRest(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertNull(order.getClientOrderId());
        assertNull(order.getSide());
        assertNull(order.getOrderType());
        assertNull(order.getQuantity());
        assertNull(order.getPrice());
        assertEquals(0L, order.getOrderId());
    }

    @Test
    @DisplayName("Should handle missing fields in WebSocket")
    void testFromWSMissingFields() throws Exception {
        // Given
        String json = """
                {
                    "s": "BTCUSDT"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromWS(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertNull(order.getClientOrderId());
        assertNull(order.getSide());
        assertNull(order.getOrderType());
        assertNull(order.getQuantity());
        assertNull(order.getPrice());
        assertEquals(0L, order.getOrderId());
    }

    @Test
    @DisplayName("Should handle empty string values in REST")
    void testFromRestEmptyStrings() throws Exception {
        // Given
        String json = """
                {
                    "symbol": "BTCUSDT",
                    "origQty": "",
                    "price": "",
                    "avgPrice": ""
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromRest(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertNull(order.getQuantity());
        assertNull(order.getPrice());
        assertNull(order.getAveragePrice());
    }

    @Test
    @DisplayName("Should handle empty string values in WebSocket")
    void testFromWSEmptyStrings() throws Exception {
        // Given
        String json = """
                {
                    "s": "BTCUSDT",
                    "q": "",
                    "p": "",
                    "ap": ""
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromWS(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertNull(order.getQuantity());
        assertNull(order.getPrice());
        assertNull(order.getAveragePrice());
    }

    @Test
    @DisplayName("Should handle invalid number formats")
    void testInvalidNumberFormats() throws Exception {
        // Given
        String json = """
                {
                    "symbol": "BTCUSDT",
                    "origQty": "invalid",
                    "price": "not_a_number",
                    "avgPrice": "NaN"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromRest(node);

        // Then
        assertNotNull(order);
        assertEquals("BTCUSDT", order.getSymbol());
        assertNull(order.getQuantity());
        assertNull(order.getPrice());
        assertNull(order.getAveragePrice());
    }

    @Test
    @DisplayName("Should handle valid enum values")
    void testValidEnumValues() throws Exception {
        // Given
        String json = """
                {
                    "symbol": "BTCUSDT",
                    "side": "buy",
                    "status": "filled"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromRest(node);

        // Then
        assertNotNull(order);
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
    }

    @Test
    @DisplayName("Should handle case insensitive enum parsing")
    void testCaseInsensitiveEnumParsing() throws Exception {
        // Given
        String json = """
                {
                    "symbol": "BTCUSDT",
                    "side": "SeLl",
                    "status": "FiLlEd"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        // When
        Order order = OrderMapper.fromRest(node);

        // Then
        assertNotNull(order);
        assertEquals(OrderSide.SELL, order.getSide());
        assertEquals(OrderStatus.FILLED, order.getOrderStatus());
    }
} 
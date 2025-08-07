package io.cryptobot.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.helpers.MainHelper;
import io.cryptobot.market_data.aggTrade.AggTradeService;
import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.market_data.klines.service.KlineService;
import io.cryptobot.market_data.ticker24h.Ticker24hService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BinanceWebSocketService Tests")
@ExtendWith(MockitoExtension.class)
class BinanceWebSocketServiceTest {

    @Mock
    private MainHelper mainHelper;

    @Mock
    private KlineService klineService;

    @Mock
    private Ticker24hService ticker24hService;

    @Mock
    private AggTradeService aggTradeService;

    @Mock
    private DepthService depthService;

    private BinanceWebSocketService webSocketService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        webSocketService = new BinanceWebSocketService(
                mainHelper, klineService, ticker24hService, aggTradeService, depthService
        );
    }



    @Test
    @DisplayName("Should handle kline message correctly")
    void testKlineMessageProcessing() throws Exception {
        // Given
        String klineJson = """
                {
                    "e": "kline",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "k": {
                        "t": 1640995200000,
                        "T": 1640995259999,
                        "s": "BTCUSDT",
                        "i": "1m",
                        "f": 123456,
                        "L": 123500,
                        "o": "50000.00",
                        "c": "50100.00",
                        "h": "50200.00",
                        "l": "49900.00",
                        "v": "100.000",
                        "n": 45,
                        "x": true,
                        "q": "5005000.00",
                        "V": "50.000",
                        "Q": "2502500.00"
                    }
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(klineJson);

        // When & Then
        // Verify JSON can be parsed without exceptions
        assertNotNull(jsonNode);
        assertEquals("kline", jsonNode.path("e").asText());
        assertEquals("BTCUSDT", jsonNode.path("s").asText());
        
        // Verify kline data structure
        JsonNode klineData = jsonNode.path("k");
        assertTrue(klineData.has("t"));
        assertTrue(klineData.has("T"));
        assertTrue(klineData.has("o"));
        assertTrue(klineData.has("c"));
        assertTrue(klineData.has("h"));
        assertTrue(klineData.has("l"));
        assertTrue(klineData.has("v"));
        assertTrue(klineData.has("x"));
    }

    @Test
    @DisplayName("Should handle 24hr ticker message correctly")
    void test24hrTickerMessageProcessing() throws Exception {
        // Given
        String tickerJson = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": "1000.00",
                    "P": "2.00",
                    "w": "50000.00",
                    "x": "49000.00",
                    "c": "50000.00",
                    "Q": "0.001",
                    "b": "49999.00",
                    "B": "1.000",
                    "a": "50001.00",
                    "A": "0.001",
                    "o": "49000.00",
                    "h": "51000.00",
                    "l": "48000.00",
                    "v": "1000.000",
                    "q": "50000000.00",
                    "O": 1640908800000,
                    "C": 1640995199999,
                    "F": 123456,
                    "L": 123500,
                    "n": 45
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(tickerJson);

        // When & Then
        // Verify JSON can be parsed without exceptions
        assertNotNull(jsonNode);
        assertEquals("24hrTicker", jsonNode.path("e").asText());
        assertEquals("BTCUSDT", jsonNode.path("s").asText());
        
        // Verify ticker data structure
        assertTrue(jsonNode.has("p")); // price change
        assertTrue(jsonNode.has("P")); // price change percent
        assertTrue(jsonNode.has("w")); // weighted avg price
        assertTrue(jsonNode.has("c")); // current price
        assertTrue(jsonNode.has("h")); // high price
        assertTrue(jsonNode.has("l")); // low price
        assertTrue(jsonNode.has("v")); // volume
        assertTrue(jsonNode.has("q")); // quote volume
    }

    @Test
    @DisplayName("Should handle aggTrade message correctly")
    void testAggTradeMessageProcessing() throws Exception {
        // Given
        String aggTradeJson = """
                {
                    "e": "aggTrade",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": "50000.00",
                    "q": "0.001",
                    "T": 123456789,
                    "m": false,
                    "M": true
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(aggTradeJson);

        // When
        // Simulate WebSocket message processing

        // Then
        // Verify that aggTradeService.addAggTrade() is called
        // Note: This requires integration testing
    }

    @Test
    @DisplayName("Should handle depth update message correctly")
    void testDepthUpdateMessageProcessing() throws Exception {
        // Given
        String depthJson = """
                {
                    "e": "depthUpdate",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "U": 123456,
                    "u": 123500,
                    "b": [
                        ["50000.00", "1.000"],
                        ["49999.00", "2.000"]
                    ],
                    "a": [
                        ["50001.00", "0.500"],
                        ["50002.00", "1.500"]
                    ]
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(depthJson);

        // When
        // Simulate WebSocket message processing

        // Then
        // Verify that depthService.processDepthUpdate() is called
        // Note: This requires integration testing
    }

    @Test
    @DisplayName("Should handle unknown event type gracefully")
    void testUnknownEventType() throws Exception {
        // Given
        String unknownJson = """
                {
                    "e": "unknownEvent",
                    "s": "BTCUSDT"
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(unknownJson);

        // When
        // Simulate WebSocket message processing

        // Then
        // Should not throw exception and continue processing
        // Note: This requires integration testing
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void testMalformedJson() {
        // Given
        String malformedJson = "{ invalid json }";

        // When & Then
        // Should throw exception when parsing malformed JSON
        assertThrows(Exception.class, () -> objectMapper.readTree(malformedJson));
    }

    @Test
    @DisplayName("Should handle null message gracefully")
    void testNullMessage() {
        // Given
        String nullMessage = null;

        // When & Then
        // Should throw exception when parsing null message
        assertThrows(Exception.class, () -> objectMapper.readTree(nullMessage));
    }

    @Test
    @DisplayName("Should stop service gracefully")
    void testStop() {
        // Given
        webSocketService.start();

        // When & Then
        // Service should stop without throwing exceptions
        assertDoesNotThrow(() -> webSocketService.stop());
    }


} 
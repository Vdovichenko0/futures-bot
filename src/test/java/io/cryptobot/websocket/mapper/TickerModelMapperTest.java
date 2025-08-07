package io.cryptobot.websocket.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.websocket.model.TickerModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TickerModelMapper Tests")
class TickerModelMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should map valid ticker JSON correctly")
    void testMapFromRestApiValidJson() throws Exception {
        // Given
        String json = """
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
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        assertEquals(new BigDecimal("1000.00"), ticker.getPriceChange());
        assertEquals(new BigDecimal("2.00"), ticker.getPriceChangePct());
        assertEquals(new BigDecimal("50000.00"), ticker.getWeightedAvg());
        assertEquals(new BigDecimal("49000.00"), ticker.getFirstTradePrice());
        assertEquals(new BigDecimal("50000.00"), ticker.getLastPrice());
        assertEquals(new BigDecimal("0.001"), ticker.getLastQty());
        assertEquals(new BigDecimal("49999.00"), ticker.getBidPrice());
        assertEquals(new BigDecimal("1.000"), ticker.getBidQty());
        assertEquals(new BigDecimal("50001.00"), ticker.getAskPrice());
        assertEquals(new BigDecimal("0.001"), ticker.getAskQty());
        assertEquals(new BigDecimal("49000.00"), ticker.getOpenPrice());
        assertEquals(new BigDecimal("51000.00"), ticker.getHighPrice());
        assertEquals(new BigDecimal("48000.00"), ticker.getLowPrice());
        assertEquals(new BigDecimal("1000.000"), ticker.getVolume());
        assertEquals(new BigDecimal("50000000.00"), ticker.getQuoteVolume());
        assertEquals(1640908800000L, ticker.getStatisticsOpenTime());
        assertEquals(1640995199999L, ticker.getStatisticsCloseTime());
        assertEquals(123456L, ticker.getFirstTradeId());
        assertEquals(123500L, ticker.getLastTradeId());
        assertEquals(45L, ticker.getTotalTrades());
    }

    @Test
    @DisplayName("Should handle null JSON")
    void testMapFromRestApiNullJson() {
        // Given
        JsonNode jsonNode = null;

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNull(ticker);
    }

    @Test
    @DisplayName("Should handle empty JSON")
    void testMapFromRestApiEmptyJson() throws Exception {
        // Given
        String json = "{}";
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNull(ticker);
    }

    @Test
    @DisplayName("Should handle JSON with missing fields")
    void testMapFromRestApiMissingFields() throws Exception {
        // Given
        String json = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT"
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        
        // Missing numeric fields should be BigDecimal.ZERO
        assertEquals(BigDecimal.ZERO, ticker.getPriceChange());
        assertEquals(BigDecimal.ZERO, ticker.getPriceChangePct());
        assertEquals(BigDecimal.ZERO, ticker.getWeightedAvg());
        assertEquals(BigDecimal.ZERO, ticker.getFirstTradePrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastQty());
        assertEquals(BigDecimal.ZERO, ticker.getBidPrice());
        assertEquals(BigDecimal.ZERO, ticker.getBidQty());
        assertEquals(BigDecimal.ZERO, ticker.getAskPrice());
        assertEquals(BigDecimal.ZERO, ticker.getAskQty());
        assertEquals(BigDecimal.ZERO, ticker.getOpenPrice());
        assertEquals(BigDecimal.ZERO, ticker.getHighPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLowPrice());
        assertEquals(BigDecimal.ZERO, ticker.getVolume());
        assertEquals(BigDecimal.ZERO, ticker.getQuoteVolume());
        
        // Missing long fields should be 0
        assertEquals(0L, ticker.getStatisticsOpenTime());
        assertEquals(0L, ticker.getStatisticsCloseTime());
        assertEquals(0L, ticker.getFirstTradeId());
        assertEquals(0L, ticker.getLastTradeId());
        assertEquals(0L, ticker.getTotalTrades());
    }

    @Test
    @DisplayName("Should handle JSON with null numeric fields")
    void testMapFromRestApiNullNumericFields() throws Exception {
        // Given
        String json = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": null,
                    "P": null,
                    "w": null,
                    "x": null,
                    "c": null,
                    "Q": null,
                    "b": null,
                    "B": null,
                    "a": null,
                    "A": null,
                    "o": null,
                    "h": null,
                    "l": null,
                    "v": null,
                    "q": null,
                    "O": 1640908800000,
                    "C": 1640995199999,
                    "F": 123456,
                    "L": 123500,
                    "n": 45
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        
        // Null numeric fields should be BigDecimal.ZERO
        assertEquals(BigDecimal.ZERO, ticker.getPriceChange());
        assertEquals(BigDecimal.ZERO, ticker.getPriceChangePct());
        assertEquals(BigDecimal.ZERO, ticker.getWeightedAvg());
        assertEquals(BigDecimal.ZERO, ticker.getFirstTradePrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastQty());
        assertEquals(BigDecimal.ZERO, ticker.getBidPrice());
        assertEquals(BigDecimal.ZERO, ticker.getBidQty());
        assertEquals(BigDecimal.ZERO, ticker.getAskPrice());
        assertEquals(BigDecimal.ZERO, ticker.getAskQty());
        assertEquals(BigDecimal.ZERO, ticker.getOpenPrice());
        assertEquals(BigDecimal.ZERO, ticker.getHighPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLowPrice());
        assertEquals(BigDecimal.ZERO, ticker.getVolume());
        assertEquals(BigDecimal.ZERO, ticker.getQuoteVolume());
        
        // Non-null long fields should have correct values
        assertEquals(1640908800000L, ticker.getStatisticsOpenTime());
        assertEquals(1640995199999L, ticker.getStatisticsCloseTime());
        assertEquals(123456L, ticker.getFirstTradeId());
        assertEquals(123500L, ticker.getLastTradeId());
        assertEquals(45L, ticker.getTotalTrades());
    }

    @Test
    @DisplayName("Should handle JSON with empty string numeric fields")
    void testMapFromRestApiEmptyStringNumericFields() throws Exception {
        // Given
        String json = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": "",
                    "P": "",
                    "w": "",
                    "x": "",
                    "c": "",
                    "Q": "",
                    "b": "",
                    "B": "",
                    "a": "",
                    "A": "",
                    "o": "",
                    "h": "",
                    "l": "",
                    "v": "",
                    "q": "",
                    "O": 1640908800000,
                    "C": 1640995199999,
                    "F": 123456,
                    "L": 123500,
                    "n": 45
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        
        // Empty string numeric fields should be BigDecimal.ZERO
        assertEquals(BigDecimal.ZERO, ticker.getPriceChange());
        assertEquals(BigDecimal.ZERO, ticker.getPriceChangePct());
        assertEquals(BigDecimal.ZERO, ticker.getWeightedAvg());
        assertEquals(BigDecimal.ZERO, ticker.getFirstTradePrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastQty());
        assertEquals(BigDecimal.ZERO, ticker.getBidPrice());
        assertEquals(BigDecimal.ZERO, ticker.getBidQty());
        assertEquals(BigDecimal.ZERO, ticker.getAskPrice());
        assertEquals(BigDecimal.ZERO, ticker.getAskQty());
        assertEquals(BigDecimal.ZERO, ticker.getOpenPrice());
        assertEquals(BigDecimal.ZERO, ticker.getHighPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLowPrice());
        assertEquals(BigDecimal.ZERO, ticker.getVolume());
        assertEquals(BigDecimal.ZERO, ticker.getQuoteVolume());
        
        // Non-empty long fields should have correct values
        assertEquals(1640908800000L, ticker.getStatisticsOpenTime());
        assertEquals(1640995199999L, ticker.getStatisticsCloseTime());
        assertEquals(123456L, ticker.getFirstTradeId());
        assertEquals(123500L, ticker.getLastTradeId());
        assertEquals(45L, ticker.getTotalTrades());
    }

    @Test
    @DisplayName("Should handle JSON with zero values")
    void testMapFromRestApiZeroValues() throws Exception {
        // Given
        String json = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": "0",
                    "P": "0",
                    "w": "0",
                    "x": "0",
                    "c": "0",
                    "Q": "0",
                    "b": "0",
                    "B": "0",
                    "a": "0",
                    "A": "0",
                    "o": "0",
                    "h": "0",
                    "l": "0",
                    "v": "0",
                    "q": "0",
                    "O": 0,
                    "C": 0,
                    "F": 0,
                    "L": 0,
                    "n": 0
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        
        // Zero values should be preserved
        assertEquals(BigDecimal.ZERO, ticker.getPriceChange());
        assertEquals(BigDecimal.ZERO, ticker.getPriceChangePct());
        assertEquals(BigDecimal.ZERO, ticker.getWeightedAvg());
        assertEquals(BigDecimal.ZERO, ticker.getFirstTradePrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLastQty());
        assertEquals(BigDecimal.ZERO, ticker.getBidPrice());
        assertEquals(BigDecimal.ZERO, ticker.getBidQty());
        assertEquals(BigDecimal.ZERO, ticker.getAskPrice());
        assertEquals(BigDecimal.ZERO, ticker.getAskQty());
        assertEquals(BigDecimal.ZERO, ticker.getOpenPrice());
        assertEquals(BigDecimal.ZERO, ticker.getHighPrice());
        assertEquals(BigDecimal.ZERO, ticker.getLowPrice());
        assertEquals(BigDecimal.ZERO, ticker.getVolume());
        assertEquals(BigDecimal.ZERO, ticker.getQuoteVolume());
        assertEquals(0L, ticker.getStatisticsOpenTime());
        assertEquals(0L, ticker.getStatisticsCloseTime());
        assertEquals(0L, ticker.getFirstTradeId());
        assertEquals(0L, ticker.getLastTradeId());
        assertEquals(0L, ticker.getTotalTrades());
    }

    @Test
    @DisplayName("Should handle JSON with large decimal values")
    void testMapFromRestApiLargeDecimalValues() throws Exception {
        // Given
        String json = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": "1234567.890123456789",
                    "P": "99.999999999999",
                    "w": "987654321.123456789",
                    "x": "0.000000001",
                    "c": "999999999.999999999",
                    "Q": "0.000000001",
                    "b": "123456789.987654321",
                    "B": "999999999.999999999",
                    "a": "987654321.123456789",
                    "A": "0.000000001",
                    "o": "0.000000001",
                    "h": "999999999.999999999",
                    "l": "0.000000001",
                    "v": "999999999.999999999",
                    "q": "999999999999.999999999",
                    "O": 1640908800000,
                    "C": 1640995199999,
                    "F": 1234567890123456789,
                    "L": 987654321098765432,
                    "n": 999999999
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        
        // Large decimal values should be preserved with precision
        assertEquals(new BigDecimal("1234567.890123456789"), ticker.getPriceChange());
        assertEquals(new BigDecimal("99.999999999999"), ticker.getPriceChangePct());
        assertEquals(new BigDecimal("987654321.123456789"), ticker.getWeightedAvg());
        assertEquals(new BigDecimal("0.000000001"), ticker.getFirstTradePrice());
        assertEquals(new BigDecimal("999999999.999999999"), ticker.getLastPrice());
        assertEquals(new BigDecimal("0.000000001"), ticker.getLastQty());
        assertEquals(new BigDecimal("123456789.987654321"), ticker.getBidPrice());
        assertEquals(new BigDecimal("999999999.999999999"), ticker.getBidQty());
        assertEquals(new BigDecimal("987654321.123456789"), ticker.getAskPrice());
        assertEquals(new BigDecimal("0.000000001"), ticker.getAskQty());
        assertEquals(new BigDecimal("0.000000001"), ticker.getOpenPrice());
        assertEquals(new BigDecimal("999999999.999999999"), ticker.getHighPrice());
        assertEquals(new BigDecimal("0.000000001"), ticker.getLowPrice());
        assertEquals(new BigDecimal("999999999.999999999"), ticker.getVolume());
        assertEquals(new BigDecimal("999999999999.999999999"), ticker.getQuoteVolume());
        assertEquals(1640908800000L, ticker.getStatisticsOpenTime());
        assertEquals(1640995199999L, ticker.getStatisticsCloseTime());
        assertEquals(1234567890123456789L, ticker.getFirstTradeId());
        assertEquals(987654321098765432L, ticker.getLastTradeId());
        assertEquals(999999999L, ticker.getTotalTrades());
    }

    @Test
    @DisplayName("Should handle JSON with negative values")
    void testMapFromRestApiNegativeValues() throws Exception {
        // Given
        String json = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": "-1000.00",
                    "P": "-2.00",
                    "w": "-50000.00",
                    "x": "-49000.00",
                    "c": "-50000.00",
                    "Q": "-0.001",
                    "b": "-49999.00",
                    "B": "-1.000",
                    "a": "-50001.00",
                    "A": "-0.001",
                    "o": "-49000.00",
                    "h": "-51000.00",
                    "l": "-48000.00",
                    "v": "-1000.000",
                    "q": "-50000000.00",
                    "O": 1640908800000,
                    "C": 1640995199999,
                    "F": 123456,
                    "L": 123500,
                    "n": 45
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        
        // Negative values should be preserved
        assertEquals(new BigDecimal("-1000.00"), ticker.getPriceChange());
        assertEquals(new BigDecimal("-2.00"), ticker.getPriceChangePct());
        assertEquals(new BigDecimal("-50000.00"), ticker.getWeightedAvg());
        assertEquals(new BigDecimal("-49000.00"), ticker.getFirstTradePrice());
        assertEquals(new BigDecimal("-50000.00"), ticker.getLastPrice());
        assertEquals(new BigDecimal("-0.001"), ticker.getLastQty());
        assertEquals(new BigDecimal("-49999.00"), ticker.getBidPrice());
        assertEquals(new BigDecimal("-1.000"), ticker.getBidQty());
        assertEquals(new BigDecimal("-50001.00"), ticker.getAskPrice());
        assertEquals(new BigDecimal("-0.001"), ticker.getAskQty());
        assertEquals(new BigDecimal("-49000.00"), ticker.getOpenPrice());
        assertEquals(new BigDecimal("-51000.00"), ticker.getHighPrice());
        assertEquals(new BigDecimal("-48000.00"), ticker.getLowPrice());
        assertEquals(new BigDecimal("-1000.000"), ticker.getVolume());
        assertEquals(new BigDecimal("-50000000.00"), ticker.getQuoteVolume());
        assertEquals(1640908800000L, ticker.getStatisticsOpenTime());
        assertEquals(1640995199999L, ticker.getStatisticsCloseTime());
        assertEquals(123456L, ticker.getFirstTradeId());
        assertEquals(123500L, ticker.getLastTradeId());
        assertEquals(45L, ticker.getTotalTrades());
    }

    @Test
    @DisplayName("Should handle JSON with scientific notation")
    void testMapFromRestApiScientificNotation() throws Exception {
        // Given
        String json = """
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSDT",
                    "p": "1.23E-6",
                    "P": "1.23E+6",
                    "w": "1.23E-3",
                    "x": "1.23E+3",
                    "c": "1.23E-9",
                    "Q": "1.23E+9",
                    "b": "1.23E-12",
                    "B": "1.23E+12",
                    "a": "1.23E-15",
                    "A": "1.23E+15",
                    "o": "1.23E-18",
                    "h": "1.23E+18",
                    "l": "1.23E-21",
                    "v": "1.23E+21",
                    "q": "1.23E-24",
                    "O": 1640908800000,
                    "C": 1640995199999,
                    "F": 123456,
                    "L": 123500,
                    "n": 45
                }
                """;
        JsonNode jsonNode = objectMapper.readTree(json);

        // When
        TickerModel ticker = TickerModelMapper.mapFromRestApi(jsonNode);

        // Then
        assertNotNull(ticker);
        assertEquals("24hrTicker", ticker.getEventType());
        assertEquals(1640995200000L, ticker.getEventTime());
        assertEquals("BTCUSDT", ticker.getSymbol());
        
        // Scientific notation should be parsed correctly
        assertEquals(new BigDecimal("1.23E-6"), ticker.getPriceChange());
        assertEquals(new BigDecimal("1.23E+6"), ticker.getPriceChangePct());
        assertEquals(new BigDecimal("1.23E-3"), ticker.getWeightedAvg());
        assertEquals(new BigDecimal("1.23E+3"), ticker.getFirstTradePrice());
        assertEquals(new BigDecimal("1.23E-9"), ticker.getLastPrice());
        assertEquals(new BigDecimal("1.23E+9"), ticker.getLastQty());
        assertEquals(new BigDecimal("1.23E-12"), ticker.getBidPrice());
        assertEquals(new BigDecimal("1.23E+12"), ticker.getBidQty());
        assertEquals(new BigDecimal("1.23E-15"), ticker.getAskPrice());
        assertEquals(new BigDecimal("1.23E+15"), ticker.getAskQty());
        assertEquals(new BigDecimal("1.23E-18"), ticker.getOpenPrice());
        assertEquals(new BigDecimal("1.23E+18"), ticker.getHighPrice());
        assertEquals(new BigDecimal("1.23E-21"), ticker.getLowPrice());
        assertEquals(new BigDecimal("1.23E+21"), ticker.getVolume());
        assertEquals(new BigDecimal("1.23E-24"), ticker.getQuoteVolume());
        assertEquals(1640908800000L, ticker.getStatisticsOpenTime());
        assertEquals(1640995199999L, ticker.getStatisticsCloseTime());
        assertEquals(123456L, ticker.getFirstTradeId());
        assertEquals(123500L, ticker.getLastTradeId());
        assertEquals(45L, ticker.getTotalTrades());
    }
} 
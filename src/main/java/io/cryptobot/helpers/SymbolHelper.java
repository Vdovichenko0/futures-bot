package io.cryptobot.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.configs.service.AppConfig;
import jakarta.annotation.PostConstruct;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@UtilityClass
public class SymbolHelper {
    //todo cache
//    @PostConstruct
//    public void init (){
//        getSizeModel("XRPUSDT");
//    }

    public SizeModel getSizeModel(String symbol) {
        try {
            String url = AppConfig.BINANCE_URL+"/fapi/v1/exchangeInfo?symbol=" + symbol.toUpperCase();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = new ObjectMapper().readTree(response.body());
            JsonNode filters = root.get("symbols").get(0).get("filters");

            BigDecimal tickSize = BigDecimal.ZERO;
            BigDecimal lotSize = BigDecimal.ZERO;
            BigDecimal minQty = BigDecimal.ZERO;
            BigDecimal minNotional = BigDecimal.ZERO;

            for (JsonNode filter : filters) {
                String type = filter.get("filterType").asText();
                switch (type) {
                    case "PRICE_FILTER" -> tickSize = new BigDecimal(filter.get("tickSize").asText());
                    case "LOT_SIZE" -> {
                        lotSize = new BigDecimal(filter.get("stepSize").asText());
                        minQty = new BigDecimal(filter.get("minQty").asText());
                    }
                    case "MIN_NOTIONAL" -> minNotional = new BigDecimal(filter.get("notional").asText());
                }
            }

            return SizeModel.builder()
                    .tickSize(tickSize.stripTrailingZeros())
                    .lotSize(lotSize.stripTrailingZeros())
                    .minCount(minQty.stripTrailingZeros())     // минимальное кол-во
                    .minAmount(minNotional.stripTrailingZeros()) // минимальная сумма в $
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to fetch SizeModel for symbol: " + symbol, e);
        }
    }

    public static Map<String, SizeModel> getSizeModels(List<String> symbols) {
        try {
            Set<String> upperSymbols = symbols.stream()
                    .map(String::toUpperCase)
                    .collect(toSet());

            String url = AppConfig.BINANCE_URL + "/fapi/v1/exchangeInfo";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode symbolNodes = root.get("symbols");
            if (symbolNodes == null || !symbolNodes.isArray()) {
                throw new IllegalStateException("Unexpected exchangeInfo structure: missing symbols array");
            }

            Map<String, SizeModel> result = new HashMap<>();

            for (JsonNode symbolNode : symbolNodes) {
                String symbol = symbolNode.get("symbol").asText();
                if (!upperSymbols.contains(symbol)) continue; // пропускаем ненужные

                BigDecimal tickSize = BigDecimal.ZERO;
                BigDecimal lotSize = BigDecimal.ZERO;
                BigDecimal minQty = BigDecimal.ZERO;
                BigDecimal minNotional = BigDecimal.ZERO;

                JsonNode filters = symbolNode.get("filters");
                if (filters != null && filters.isArray()) {
                    for (JsonNode filter : filters) {
                        String type = filter.has("filterType") ? filter.get("filterType").asText() : "";
                        switch (type) {
                            case "PRICE_FILTER" -> {
                                if (filter.has("tickSize")) {
                                    tickSize = new BigDecimal(filter.get("tickSize").asText());
                                }
                            }
                            case "LOT_SIZE" -> {
                                if (filter.has("stepSize")) {
                                    lotSize = new BigDecimal(filter.get("stepSize").asText());
                                }
                                if (filter.has("minQty")) {
                                    minQty = new BigDecimal(filter.get("minQty").asText());
                                }
                            }
                            case "MIN_NOTIONAL", "NOTIONAL" -> { // на всякий случай оба варианта
                                if (filter.has("notional")) {
                                    minNotional = new BigDecimal(filter.get("notional").asText());
                                }
                            }
                        }
                    }
                }

                SizeModel model = SizeModel.builder()
                        .tickSize(tickSize.stripTrailingZeros())
                        .lotSize(lotSize.stripTrailingZeros())
                        .minCount(minQty.stripTrailingZeros())
                        .minAmount(minNotional.stripTrailingZeros())
                        .build();

                result.put(symbol, model);
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to fetch SizeModels for symbols: " + symbols, e);
        }
    }
}


package io.cryptobot.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryptobot.binance.trade.trade_plan.model.SizeModel;
import io.cryptobot.configs.service.AppConfig;
import lombok.experimental.UtilityClass;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@UtilityClass
public class SymbolHelper {
    //todo cache
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

}


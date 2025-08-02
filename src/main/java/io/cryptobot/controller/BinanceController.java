package io.cryptobot.controller;

import io.cryptobot.binance.BinanceService;
import io.cryptobot.binance.BinanceServiceImpl;
import io.cryptobot.klines.enums.IntervalE;
import io.cryptobot.klines.model.KlineModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/binance")
@RequiredArgsConstructor
public class BinanceController {

    private final BinanceService binanceService;

    @GetMapping("/account")
    public ResponseEntity<String> getAccountInfo() {
        try {
            String accountInfo = binanceService.getAccountInfo();
            return ResponseEntity.ok(accountInfo);
        } catch (Exception e) {
            log.error("Failed to get account info", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Failed to get account info: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/klines")
    public ResponseEntity<List<KlineModel>> getKlines(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            IntervalE intervalEnum = IntervalE.fromString(interval);
            List<KlineModel> klines = binanceService.getKlines(symbol, intervalEnum, limit);
            return ResponseEntity.ok(klines);
        } catch (Exception e) {
            log.error("Failed to get klines", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testConnection() {
        try {
            boolean isConnected = ((BinanceServiceImpl) binanceService).testConnection();
            
            String response = String.format(
                "{\"connected\": %s, \"message\": \"Connection test completed\"}",
                isConnected
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Connection test failed", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Connection test failed: " + e.getMessage() + "\"}");
        }
    }
} 
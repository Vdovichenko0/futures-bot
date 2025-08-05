package io.cryptobot.controller;

import io.cryptobot.utils.logging.TradingLogAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/trading/logs")
@RequiredArgsConstructor
public class TradingLogController {
    
    private final TradingLogAnalyzer logAnalyzer;
    
    @GetMapping("/decisions/{symbol}")
    public ResponseEntity<List<TradingLogAnalyzer.SignalEntry>> getFinalDecisions(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().format(T(java.time.format.DateTimeFormatter).ofPattern('yyyy-MM-dd'))}") 
            String date) {
        
        List<TradingLogAnalyzer.SignalEntry> decisions = logAnalyzer.getFinalDecisions(symbol.toUpperCase(), date);
        return ResponseEntity.ok(decisions);
    }
    
} 
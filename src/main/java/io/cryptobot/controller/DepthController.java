package io.cryptobot.controller;

import io.cryptobot.market_data.depth.DepthService;
import io.cryptobot.market_data.depth.DepthStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/depth")
@RequiredArgsConstructor
public class DepthController {
    
    private final DepthService depthService;
    
    /**
     * Получает статистику по всем стаканам
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, DepthStats>> getDepthStats() {
        Map<String, DepthStats> stats = depthService.getDepthStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Получает статистику по конкретному символу
     */
    @GetMapping("/stats/{symbol}")
    public ResponseEntity<DepthStats> getDepthStatsForSymbol(@PathVariable String symbol) {
        Map<String, DepthStats> allStats = depthService.getDepthStats();
        DepthStats stats = allStats.get(symbol.toUpperCase());
        
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Проверяет наличие стакана для символа
     */
    @GetMapping("/has/{symbol}")
    public ResponseEntity<Boolean> hasOrderBook(@PathVariable String symbol) {
        boolean hasOrderBook = depthService.hasOrderBook(symbol);
        return ResponseEntity.ok(hasOrderBook);
    }
} 
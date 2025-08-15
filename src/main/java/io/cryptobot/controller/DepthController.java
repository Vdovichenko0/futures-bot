package io.cryptobot.controller;

import io.cryptobot.market_data.depth.DepthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/depth")
@RequiredArgsConstructor
public class DepthController {
    
    private final DepthService depthService;
    
    /**
     * Проверяет наличие стакана для символа
     */
    @GetMapping("/has/{symbol}")
    public ResponseEntity<Boolean> hasOrderBook(@PathVariable String symbol) {
        boolean hasOrderBook = depthService.hasOrderBook(symbol);
        return ResponseEntity.ok(hasOrderBook);
    }
} 
package io.cryptobot.utils.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class TradingLogWriter {
    
    private static final String LOGS_DIR = "logs/trading";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();
    
    public TradingLogWriter() {
        createLogsDirectory();
    }
    
    private void createLogsDirectory() {
        File dir = new File(LOGS_DIR);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("Created trading logs directory: {}", LOGS_DIR);
            } else {
                log.error("Failed to create trading logs directory: {}", LOGS_DIR);
            }
        }
    }
    
    /**
     * Записывает лог торговой операции в файл
     */
    public void writeTradeLog(String symbol, String message) {
        String fileName = getLogFileName(symbol);
        ReentrantLock lock = fileLocks.computeIfAbsent(fileName, k -> new ReentrantLock());
        
        lock.lock();
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName, true))) {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String logEntry = String.format("[%s] %s", timestamp, message);
            writer.println(logEntry);
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to write trade log for {}: {}", symbol, e.getMessage());
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Записывает результат анализа торгового плана
     */
    public void writeTradeAnalysis(String symbol, TradeAnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TRADE ANALYSIS ===\n");
        sb.append(String.format("Symbol: %s\n", symbol));
        sb.append(String.format("EMA Trend: %s (EMA20=%s, EMA50=%s)\n", 
            result.getEmaDirection(), result.getEma20(), result.getEma50()));
        sb.append(String.format("Volume Ratio: %s (%s)\n", 
            result.getVolumeRatio(), result.getVolumeDirection()));
        sb.append(String.format("Order Book Imbalance: %s%% (%s)\n", 
            result.getImbalance(), result.getImbalanceDirection()));
        sb.append(String.format("Long/Short Ratio: %s%% (%s)\n", 
            result.getLongShortRatio(), result.getLongShortDirection()));
        sb.append(String.format("Final Signal: %s\n", result.getFinalSignal()));
        sb.append("===================\n");
        
        writeTradeLog(symbol, sb.toString());
    }
    
    /**
     * Записывает ошибку обработки торгового плана
     */
    public void writeTradeError(String symbol, String error) {
        writeTradeLog(symbol, "ERROR: " + error);
    }
    
    private String getLogFileName(String symbol) {
        String date = LocalDateTime.now().format(DATE_FORMATTER);
        return String.format("%s/%s_%s.log", LOGS_DIR, symbol.toLowerCase(), date);
    }
    
    /**
     * Результат анализа торгового плана
     */
    public static class TradeAnalysisResult {
        private final String emaDirection;
        private final String ema20;
        private final String ema50;
        private final String volumeRatio;
        private final String volumeDirection;
        private final String imbalance;
        private final String imbalanceDirection;
        private final String longShortRatio;
        private final String longShortDirection;
        private final String finalSignal;
        
        public TradeAnalysisResult(String emaDirection, String ema20, String ema50,
                                 String volumeRatio, String volumeDirection,
                                 String imbalance, String imbalanceDirection,
                                 String longShortRatio, String longShortDirection,
                                 String finalSignal) {
            this.emaDirection = emaDirection;
            this.ema20 = ema20;
            this.ema50 = ema50;
            this.volumeRatio = volumeRatio;
            this.volumeDirection = volumeDirection;
            this.imbalance = imbalance;
            this.imbalanceDirection = imbalanceDirection;
            this.longShortRatio = longShortRatio;
            this.longShortDirection = longShortDirection;
            this.finalSignal = finalSignal;
        }
        
        // Getters
        public String getEmaDirection() { return emaDirection; }
        public String getEma20() { return ema20; }
        public String getEma50() { return ema50; }
        public String getVolumeRatio() { return volumeRatio; }
        public String getVolumeDirection() { return volumeDirection; }
        public String getImbalance() { return imbalance; }
        public String getImbalanceDirection() { return imbalanceDirection; }
        public String getLongShortRatio() { return longShortRatio; }
        public String getLongShortDirection() { return longShortDirection; }
        public String getFinalSignal() { return finalSignal; }
    }
} 
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
    
    private String getLogFileName(String symbol) {
        String date = LocalDateTime.now().format(DATE_FORMATTER);
        return String.format("%s/%s_%s.log", LOGS_DIR, symbol.toLowerCase(), date);
    }
} 
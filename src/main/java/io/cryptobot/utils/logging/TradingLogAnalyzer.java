package io.cryptobot.utils.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TradingLogAnalyzer {
    
    private static final String LOGS_DIR = "logs/trading";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Находит финальные решения "OK" для входа в позицию
     */
    public List<SignalEntry> getFinalDecisions(String symbol, String date) {
        return findFinalDecisions(symbol, date);
    }
    

    

    
    /**
     * Находит моменты финальных решений "OK" для входа в позицию
     */
    public List<SignalEntry> findFinalDecisions(String symbol, String date) {
        String fileName = String.format("%s/%s_%s.log", LOGS_DIR, symbol.toLowerCase(), date);
        File file = new File(fileName);
        
        if (!file.exists()) {
            return Collections.emptyList();
        }
        
        List<SignalEntry> finalDecisions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            LocalDateTime decisionTime = null;
            String signal = null;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("📊 Final Signal:")) {
                    LocalDateTime timestamp = extractTimestamp(line);
                    String currentSignal = extractSignal(line);
                    
                    // Ищем только LONG или SHORT сигналы (не NO SIGNAL)
                    if ("LONG".equals(currentSignal) || "SHORT".equals(currentSignal)) {
                        decisionTime = timestamp;
                        signal = currentSignal;
                        
                        // Ищем индикаторы для этого момента
                        Map<String, String> indicators = extractIndicatorsForDecision(fileName, timestamp);
                        
                        finalDecisions.add(new SignalEntry(
                            signal,
                            1, // Одиночное решение
                            decisionTime,
                            decisionTime,
                            indicators
                        ));
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to find final decisions for {}: {}", symbol, e.getMessage());
        }
        
        return finalDecisions;
    }
    

    
    /**
     * Получает индикаторы для конкретного момента принятия решения
     */
    private Map<String, String> extractIndicatorsForDecision(String fileName, LocalDateTime decisionTime) {
        Map<String, String> indicators = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            LocalDateTime currentTimestamp = null;
            boolean foundDecision = false;
            
            while ((line = reader.readLine()) != null) {
                LocalDateTime timestamp = extractTimestamp(line);
                if (timestamp != null) {
                    currentTimestamp = timestamp;
                    
                    // Если нашли время решения, начинаем собирать индикаторы
                    if (timestamp.equals(decisionTime)) {
                        foundDecision = true;
                    }
                    // Если прошли больше 5 секунд после решения, останавливаемся
                    else if (foundDecision && timestamp.isAfter(decisionTime.plusSeconds(5))) {
                        break;
                    }
                }
                
                if (foundDecision && currentTimestamp != null) {
                    // Ищем индикаторы в логах
                    if (line.contains("EMA →")) {
                        indicators.put("ema", line.substring(line.indexOf("EMA →")));
                    } else if (line.contains("VolRatio →")) {
                        indicators.put("volume", line.substring(line.indexOf("VolRatio →")));
                    } else if (line.contains("Imbalance →")) {
                        indicators.put("imbalance", line.substring(line.indexOf("Imbalance →")));
                    } else if (line.contains("Long%=")) {
                        indicators.put("longShort", line.substring(line.indexOf("Long%=")));
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to extract indicators for decision: {}", e.getMessage());
        }
        
        return indicators;
    }
    
    /**
     * Получает индикаторы для временного диапазона
     */
    private Map<String, String> extractIndicatorsFromLog(String fileName, LocalDateTime start, LocalDateTime end) {
        Map<String, String> indicators = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            boolean inRange = false;
            LocalDateTime currentTimestamp = null;
            
            while ((line = reader.readLine()) != null) {
                LocalDateTime timestamp = extractTimestamp(line);
                if (timestamp != null) {
                    currentTimestamp = timestamp;
                    if (timestamp.isAfter(start) && timestamp.isBefore(end)) {
                        inRange = true;
                    } else if (timestamp.isAfter(end)) {
                        break;
                    }
                }
                
                if (inRange && currentTimestamp != null) {
                    // Ищем индикаторы в логах
                    if (line.contains("EMA →")) {
                        indicators.put("ema", line.substring(line.indexOf("EMA →")));
                    } else if (line.contains("VolRatio →")) {
                        indicators.put("volume", line.substring(line.indexOf("VolRatio →")));
                    } else if (line.contains("Imbalance →")) {
                        indicators.put("imbalance", line.substring(line.indexOf("Imbalance →")));
                    } else if (line.contains("Long%=")) {
                        indicators.put("longShort", line.substring(line.indexOf("Long%=")));
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to extract indicators: {}", e.getMessage());
        }
        
        return indicators;
    }
    
    private LocalDateTime extractTimestamp(String line) {
        try {
            if (line.startsWith("[") && line.contains("]")) {
                String timeStr = line.substring(1, line.indexOf("]"));
                // Создаем LocalDateTime с текущей датой и временем из лога
                LocalDateTime now = LocalDateTime.now();
                String[] timeParts = timeStr.split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                String[] secondParts = timeParts[2].split("\\.");
                int second = Integer.parseInt(secondParts[0]);
                int millisecond = Integer.parseInt(secondParts[1]);
                
                return LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 
                                     hour, minute, second, millisecond * 1_000_000);
            }
        } catch (Exception e) {
            // Игнорируем ошибки парсинга времени
        }
        return null;
    }
    
    private String extractSignal(String line) {
        int signalIndex = line.indexOf("Final Signal: ");
        if (signalIndex != -1) {
            return line.substring(signalIndex + 14).trim();
        }
        return "UNKNOWN";
    }
    

    
    /**
     * Запись серии сигналов
     */
    public static class SignalEntry {
        private final String signal;
        private final int length;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final Map<String, String> indicators;
        
        public SignalEntry(String signal, int length, LocalDateTime startTime, LocalDateTime endTime, Map<String, String> indicators) {
            this.signal = signal;
            this.length = length;
            this.startTime = startTime;
            this.endTime = endTime;
            this.indicators = indicators;
        }
        
        // Getters
        public String getSignal() { return signal; }
        public int getLength() { return length; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public Map<String, String> getIndicators() { return indicators; }
        
        @Override
        public String toString() {
            return String.format("SignalEntry{signal='%s', length=%d, start=%s, end=%s}", 
                signal, length, startTime, endTime);
        }
    }
} 
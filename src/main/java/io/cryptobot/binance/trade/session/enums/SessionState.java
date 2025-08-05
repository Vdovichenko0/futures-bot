package io.cryptobot.binance.trade.session.enums;

public enum SessionState {
    OPEN,          // базовая позиция открыта
    TRAILING,      // активирован трейлинг
    HEDGING,       // открыт хедж
    POST_CLOSE,    // режим post-close
    FORCING,       // forcing mode
    CLOSED         // окончательно закрыта
}

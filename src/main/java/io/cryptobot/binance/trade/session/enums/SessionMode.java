package io.cryptobot.binance.trade.session.enums;

public enum SessionMode {
    SCALPING,    // обычный скальпинг (одна позиция)
    HEDGING,     // режим хеджирования
    POST_CLOSE,  // после закрытия лучшей -> ждем откат
    FORCING      // форсированный режим (активная торговля против тренда)
}

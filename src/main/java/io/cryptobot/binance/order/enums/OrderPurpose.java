package io.cryptobot.binance.order.enums;

public enum OrderPurpose {
    MAIN_OPEN,           // вход в основную позицию (открытие main position)
    MAIN_CLOSE,           // закрытие основной позиции (полное или финальное)
    MAIN_PARTIAL_CLOSE,

    HEDGE_OPEN,           // открытие хеджа
    HEDGE_CLOSE,          // закрытие хеджа (финальное)
    HEDGE_PARTIAL_CLOSE,  // частичное закрытие хеджа (если дробишь)

    AVERAGING_OPEN, //усреднение позиции
    AVERAGING_CLOSE
}

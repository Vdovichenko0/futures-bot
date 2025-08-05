package io.cryptobot.calculator;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmaValues {
    private Double ema7;
    private Double ema20;
    private Double ema50;
    private Double ema200;
}

package io.cryptobot.utils.lock.single_lock;

import io.cryptobot.configs.locks.TradePlanLockRegistry;
import io.cryptobot.configs.locks.TradeSessionLockRegistry;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Aspect
@Component
@RequiredArgsConstructor
public class LockAspect {

    private final TradePlanLockRegistry planLockRegistry;
    private final TradeSessionLockRegistry sessionLockRegistry;

    @Around("@annotation(withLock)")
    public Object aroundWithLock(ProceedingJoinPoint pjp, WithLock withLock) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] paramNames = sig.getParameterNames();
        Object[] args = pjp.getArgs();

        // 1) Найти индекс параметра-ключа
        int idx = IntStream.range(0, paramNames.length)
                .filter(i -> paramNames[i].equals(withLock.keyParam()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Lock not found: " + withLock.keyParam())
                );

        Object key = args[idx];
        // 2) Взять нужный LockRegistry
        ReentrantLock lock = switch (withLock.registry()) {
            case PLAN -> planLockRegistry.getLock((String) key);
            case SESSION -> sessionLockRegistry.getLock((String) key);
        };

        // 3) Заблокировать, выполнить метод, разблокировать
        lock.lock();
        try {
            return pjp.proceed();
        } finally {
            lock.unlock();
        }
    }
}


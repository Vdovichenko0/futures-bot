package io.cryptobot.utils.lock.many;

import io.cryptobot.configs.locks.TradePlanLockRegistry;
import io.cryptobot.configs.locks.TradeSessionLockRegistry;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.IntStream;

@Aspect
@Component
@RequiredArgsConstructor
public class ParallelizeAspect {


    private final TradePlanLockRegistry planLockRegistry;
    private final TradeSessionLockRegistry sessionLockRegistry;

    @Around("@annotation(parallel)")
    public Object aroundParallel(ProceedingJoinPoint pjp, Parallelize parallel){
        MethodSignature sig = (MethodSignature) pjp.getSignature();
//        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();
        String[] paramNames = sig.getParameterNames();

        // 1) найти индекс параметра-списка
        int idx = IntStream.range(0, paramNames.length).filter(i -> paramNames[i].equals(parallel.listArg())).findFirst().orElse(-1);
        if (idx < 0 || !(args[idx] instanceof List<?> items)) {
            throw new IllegalStateException("List argument not found: " + parallel.listArg());
        }
        //todo
        final Object lockRegistry = switch (parallel.registry()) {
            case PLAN -> planLockRegistry;
            case SESSION -> sessionLockRegistry;
        };

        // 3) запустить ParallelExecutor
        ParallelExecutor.executeInParallel(
                items,
                parallel.chunkSize(),
                parallel.threadCount(),
                elem -> { // lockProvider: получить lock по полю lockField
                    Field field = ReflectionUtils.findField(elem.getClass(), parallel.lockField());
                    ReflectionUtils.makeAccessible(field);
                    Object idVal = ReflectionUtils.getField(field, elem);

                    // Получаем lock из правильного registry
                    return switch (parallel.registry()) {
                        case PLAN -> planLockRegistry.getLock(String.valueOf(idVal));
                        case SESSION -> sessionLockRegistry.getLock(String.valueOf(idVal));
                    };
                },
                chunk -> { // chunkProcessor: вызвать оригинальный метод с этим чанком
                    Object[] newArgs = args.clone();
                    newArgs[idx] = chunk;
                    try {
                        pjp.proceed(newArgs);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        // Наши методы — void, возвращаем null
        return null;
    }
}


/*
Что делает @Around("@annotation(parallel)")
Это аспект, который перехватывает вызовы всех методов, помеченных определённой аннотацией, в данном случае — @Parallelize.

@Around — аспект, оборачивающий выполнение метода: ты можешь выполнить код до, после, или даже вместо вызова оригинального метода.
"@annotation(parallel)" — выражение Spring AOP, означающее:

"Перехватывай любой метод, который помечен аннотацией @Parallelize, и передай саму аннотацию в аргумент parallel".
 */
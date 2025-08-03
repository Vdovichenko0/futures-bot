package io.cryptobot.utils.lock.many;

import io.cryptobot.utils.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Parallelize {
    /**
     * Имя параметра метода, содержащего список портфелей.
     */
    String listArg();

    /**
     * Имя поля/метода в каждом элементе, по которому мы берем Lock:
     * например "id" (будем вызывать lockRegistry.getLock(elem.getId()))
     */
    String lockField() default "id";

    int chunkSize() default 20;
    int threadCount() default 10;

    LockType registry();
}

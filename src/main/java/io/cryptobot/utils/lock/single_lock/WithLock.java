package io.cryptobot.utils.lock.single_lock;

import io.cryptobot.utils.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithLock {
    LockType registry();

    String keyParam();
}

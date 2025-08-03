package io.cryptobot.configs.locks;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class TradePlanLockRegistry implements LockRegistry {
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(String planId) {
        return locks.computeIfAbsent(planId, k -> new ReentrantLock());
    }

    public void removeLock(String planId, ReentrantLock lock) {
        locks.remove(planId, lock);
    }
}

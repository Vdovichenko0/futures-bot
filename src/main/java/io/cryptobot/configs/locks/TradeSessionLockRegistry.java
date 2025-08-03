package io.cryptobot.configs.locks;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


@Component
public class TradeSessionLockRegistry implements LockRegistry {
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(String sessionId) {
        return locks.computeIfAbsent(sessionId, k -> new ReentrantLock());
    }

    public void removeLock(String sessionId, ReentrantLock lock) {
        locks.remove(sessionId, lock);
    }
}

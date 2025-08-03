package io.cryptobot.configs.locks;

import java.util.concurrent.locks.ReentrantLock;

public interface LockRegistry {
    ReentrantLock getLock(String key);
}

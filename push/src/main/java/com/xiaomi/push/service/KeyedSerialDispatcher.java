package com.xiaomi.push.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Dispatches tasks serially per logical key while allowing distinct keys
 * to run concurrently on the underlying bounded executor.
 */
final class KeyedSerialDispatcher<K> {
    private final Executor delegate;
    private final FailureHandler<K> failureHandler;
    private final Object lock = new Object();
    private final Map<K, Deque<Runnable>> queues = new HashMap<>();

    KeyedSerialDispatcher(Executor delegate, FailureHandler<K> failureHandler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.failureHandler = failureHandler;
    }

    void execute(K key, Runnable command) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(command, "command");

        boolean shouldSchedule = false;
        synchronized (lock) {
            Deque<Runnable> queue = queues.get(key);
            if (queue == null) {
                queue = new ArrayDeque<>();
                queues.put(key, queue);
                shouldSchedule = true;
            }
            queue.addLast(command);
        }

        if (shouldSchedule) {
            try {
                delegate.execute(() -> drainKey(key));
            } catch (RuntimeException ex) {
                // 若提交被拒绝，清理空状态或立即回退执行以防止队列永远悬挂。
                drainKey(key);
            }
        }
    }

    private void drainKey(K key) {
        while (true) {
            Runnable next;
            synchronized (lock) {
                Deque<Runnable> queue = queues.get(key);
                if (queue == null || queue.isEmpty()) {
                    queues.remove(key);
                    return;
                }
                next = queue.pollFirst();
            }
            try {
                next.run();
            } catch (Throwable failure) {
                if (failureHandler != null) {
                    try {
                        failureHandler.onFailure(key, failure);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    @FunctionalInterface
    interface FailureHandler<K> {
        void onFailure(K key, Throwable failure);
    }
}

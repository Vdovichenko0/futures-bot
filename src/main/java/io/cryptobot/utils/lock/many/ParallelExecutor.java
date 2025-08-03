package io.cryptobot.utils.lock.many;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class ParallelExecutor {

    /**
     * Параллельно обрабатывает список по чанкам:
     * 1) Разбивает на чанки указанного размера.
     * 2) Для каждого чанка захватывает по-элементно локи через lockProvider.
     * 3) Вызывает chunkProcessor.accept(chunk).
     * 4) Снимает локи.
     * 5) Ждёт завершения всех задач и затем завершает пул.
     *
     * @param items          исходный список
     * @param chunkSize      размер чанка
     * @param threadCount    число потоков в пуле
     * @param lockProvider   функция, дающая для каждого элемента ReentrantLock
     * @param chunkProcessor действие, принимающее весь чанк (например, обновить + сохранить)
     */
    public static <T> void executeInParallel(
            List<T> items,
            int chunkSize,
            int threadCount,
            Function<T, ReentrantLock> lockProvider,
            Consumer<List<T>> chunkProcessor
    ) {
        if (items == null || items.isEmpty()) return;

        // 1) разбиваем на чанки
        List<List<T>> chunks = chunkList(items, chunkSize);

        // 2) создаём пул
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (List<T> chunk : chunks) {
                futures.add(exec.submit(() -> {
                    List<ReentrantLock> locks = chunk.stream()
                            .map(lockProvider)
                            .toList();
                    locks.forEach(ReentrantLock::lock);

                    try {
                        chunkProcessor.accept(chunk);
                    } finally {
                        locks.forEach(ReentrantLock::unlock);
                    }
                }));
            }

            // ждём все
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException ignored) {
                }
            }

//            for (Future<?> f : futures) {
//                try {
//                    f.get();
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    throw new RuntimeException("Parallel execution interrupted", e);
//                } catch (ExecutionException e) {
//                    throw new RuntimeException("Exception in parallel chunk", e.getCause());
//                }
//            }
        } finally {
            exec.shutdown();
        }
    }

    private static <T> List<List<T>> chunkList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }
}

package com.simpleutils;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

/**
 * Реализация задержки выполнения кода на указанный промежуток времени.
 */
public class DelayedRunnables {

    private record DelayedRunnable(long nanoTime, Runnable runnable) {
    }

    /**
     * Очередь на выполнение.
     */
    private final PriorityQueue<DelayedRunnable> queue = new PriorityQueue<>(Comparator.comparingLong(o -> o.nanoTime));

    /**
     * Поставить код в очередь на выполнение после указанной задержки.
     *
     * @param runnable код
     * @param delay    задержка
     * @param unit     единица измерения задержки
     */
    public void submit(final Runnable runnable, final long delay, final TimeUnit unit) {
        synchronized (queue) {
            queue.add(new DelayedRunnable(System.nanoTime() + unit.toNanos(delay), runnable));
        }
    }

    /**
     * @return код для выполнения или {@code null}, если его нет
     */
    public Runnable poll() {
        synchronized (queue) {
            final DelayedRunnable delayedRunnable = queue.peek();
            return (delayedRunnable == null || System.nanoTime() < delayedRunnable.nanoTime) ? null : queue.poll().runnable;
        }
    }
}

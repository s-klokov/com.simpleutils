package com.simpleutils.quik;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

public abstract class AbstractQuikListener implements QuikListener {
    /**
     * Подключение к терминалу QUIK.
     */
    public volatile QuikConnect quikConnect = null;
    /**
     * Поток для исполнения бизнес-логики.
     */
    public volatile Thread executionThread = null;
    /**
     * Очередь на исполнение.
     */
    private final Queue<Runnable> queue = new LinkedBlockingDeque<>();

    @Override
    public void setQuikConnect(final QuikConnect quikConnect) {
        this.quikConnect = quikConnect;
    }

    @Override
    public Thread getExecutionThread() {
        return executionThread;
    }

    @Override
    public void submit(final Runnable runnable) {
        queue.add(runnable);
    }

    @Override
    public Runnable poll() {
        return queue.poll();
    }
}

package com.simpleutils.logs;

import java.util.Objects;

/**
 * Пара логгеров.
 */
public class LoggerCouple extends AbstractLogger {

    private final AbstractLogger logger1;
    private final AbstractLogger logger2;

    public LoggerCouple(final AbstractLogger logger1, final AbstractLogger logger2) {
        this.logger1 = Objects.requireNonNull(logger1);
        this.logger2 = Objects.requireNonNull(logger2);
    }

    @Override
    public void close() {
        logger1.close();
        logger2.close();
    }

    @Override
    protected void print(final int level, final String s) {
        logger1.print(level, s);
        logger2.print(level, s);
    }

    @Override
    protected void print(final int level, final String s, final Throwable thrown) {
        logger1.print(level, s, thrown);
        logger2.print(level, s, thrown);
    }
}

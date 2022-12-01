package com.simpleutils.logs;

import java.util.HashSet;
import java.util.Set;

public class MultiLogger extends AbstractLogger {

    private final Set<AbstractLogger> loggers = new HashSet<>();

    public void addLogger(final AbstractLogger logger) {
        synchronized (loggers) {
            loggers.add(logger);
        }
    }

    public void removeLogger(final AbstractLogger logger) {
        synchronized (loggers) {
            loggers.remove(logger);
        }
    }

    @Override
    public void close() {
        synchronized (loggers) {
            loggers.forEach(AbstractLogger::close);
        }
    }

    @Override
    protected void print(final int level, final String s) {
        synchronized (loggers) {
            loggers.forEach(logger -> logger.print(level, s));
        }
    }

    @Override
    protected void print(final int level, final String s, final Throwable thrown) {
        synchronized (loggers) {
            loggers.forEach(logger -> logger.print(level, s, thrown));
        }
    }
}

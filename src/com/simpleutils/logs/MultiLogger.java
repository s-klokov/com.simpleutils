package com.simpleutils.logs;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings({"UnusedReturnValue", "unused"})
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
    public MultiLogger withLogLevel(final int logLevel) {
        this.logLevel = logLevel;
        synchronized (loggers) {
            loggers.forEach(logger -> logger.withLogLevel(logLevel));
        }
        return this;
    }

    @Override
    public MultiLogger withErrLevel(final int errLevel) {
        this.errLevel = errLevel;
        synchronized (loggers) {
            loggers.forEach(logger -> logger.withErrLevel(errLevel));
        }
        return this;
    }

    @Override
    public MultiLogger withThreadNameEnabled(final boolean isThreadNameEnabled) {
        this.isThreadNameEnabled = isThreadNameEnabled;
        synchronized (loggers) {
            loggers.forEach(logger -> logger.withThreadNameEnabled(isThreadNameEnabled));
        }
        return this;
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

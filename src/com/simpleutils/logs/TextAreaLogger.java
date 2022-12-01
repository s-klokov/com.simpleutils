package com.simpleutils.logs;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * Реализация логгирования с выводом в объекты типа {@link JTextArea}.
 */
public class TextAreaLogger extends AbstractLogger {

    public volatile long logTruncationLength = 1_200_000L;
    public volatile long errTruncationLength = 120_000L;
    public volatile long logTargetLength = 1_000_000L;
    public volatile long errTargetLength = 100_000L;

    /**
     * Объект для синхронизации.
     */
    protected final Object mutex = new Object();
    /**
     * Объект для вывода сообщений.
     */
    protected JTextArea logTextArea = null;
    /**
     * Объект для дублирования сообщений об ошибках, уровня {@link #errLevel} и выше.
     */
    protected JTextArea errTextArea = null;

    @Override
    public TextAreaLogger withLogLevel(final int logLevel) {
        super.withLogLevel(logLevel);
        return this;
    }

    @Override
    public TextAreaLogger withErrLevel(final int errLevel) {
        super.withErrLevel(errLevel);
        return this;
    }

    @Override
    public TextAreaLogger withThreadNameEnabled(final boolean isThreadNameEnabled) {
        super.withThreadNameEnabled(isThreadNameEnabled);
        return this;
    }

    public TextAreaLogger withLogTextArea(final JTextArea logTextArea) {
        synchronized (mutex) {
            this.logTextArea = logTextArea;
        }
        return this;
    }

    public TextAreaLogger withErrTextArea(final JTextArea errTextArea) {
        synchronized (mutex) {
            this.errTextArea = errTextArea;
        }
        return this;
    }

    @Override
    public void close() {
        synchronized (mutex) {
            logTextArea = null;
            errTextArea = null;
        }
    }

    @Override
    protected void print(final int level, final String s) {
        synchronized (mutex) {
            if (logTextArea != null) {
                final JTextArea textArea = logTextArea;
                SwingUtilities.invokeLater(() -> {
                    truncate(textArea.getDocument(), logTruncationLength, logTargetLength);
                    textArea.append(s);
                    textArea.append("\r\n");
                });
            }
            if (errTextArea != null && level >= errLevel) {
                final JTextArea textArea = errTextArea;
                SwingUtilities.invokeLater(() -> {
                    truncate(textArea.getDocument(), errTruncationLength, errTargetLength);
                    textArea.append(s);
                    textArea.append("\r\n");
                });
            }
        }
    }

    @Override
    protected void print(final int level, final String s, final Throwable thrown) {
        final String stackTrace = stackTraceString(thrown);
        synchronized (mutex) {
            if (logTextArea != null) {
                final JTextArea textArea = logTextArea;
                SwingUtilities.invokeLater(() -> {
                    truncate(textArea.getDocument(), logTruncationLength, logTargetLength);
                    textArea.append(s);
                    textArea.append("\r\n");
                    textArea.append(stackTrace);
                    textArea.append("\r\n");
                });
            }
            if (errTextArea != null && level >= errLevel) {
                final JTextArea textArea = errTextArea;
                SwingUtilities.invokeLater(() -> {
                    truncate(textArea.getDocument(), errTruncationLength, errTargetLength);
                    textArea.append(s);
                    textArea.append("\r\n");
                    textArea.append(stackTrace);
                    textArea.append("\r\n");
                });
            }
        }
    }

    private static String stackTraceString(final Throwable thrown) {
        final StringBuilder sb = new StringBuilder();
        sb.append(thrown.getClass().getCanonicalName()).append(": ").append(thrown.getMessage()).append("\r\n");
        final StackTraceElement[] stackTrace = thrown.getStackTrace();
        for (final StackTraceElement stackTraceElement : stackTrace) {
            sb.append(stackTraceElement.toString()).append("\r\n");
        }
        return sb.toString();
    }

    private static void truncate(final Document document,
                                 final long truncationLength,
                                 final long targetLength) {
        final int len = document.getLength();
        if (len >= truncationLength) {
            try {
                document.remove(0, (int) (len - targetLength));
            } catch (final BadLocationException ignored) {
            }
        }
    }
}

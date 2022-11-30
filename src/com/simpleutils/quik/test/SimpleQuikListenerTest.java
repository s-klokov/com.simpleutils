package com.simpleutils.quik.test;

import com.simpleutils.logs.AbstractLogger;
import com.simpleutils.logs.SimpleLogger;
import com.simpleutils.quik.ClassSecCode;
import com.simpleutils.quik.QuikConnect;
import com.simpleutils.quik.SimpleQuikListener;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class SimpleQuikListenerTest {

    private final AbstractLogger logger = new SimpleLogger();

    public static void main(final String[] args) {
        new SimpleQuikListenerTest().test();
    }

    private void test() {
        logger.withLogLevel(AbstractLogger.TRACE);

        final SimpleQuikListener simpleQuikListener = new SimpleQuikListener();

        simpleQuikListener.setRequestTimeout(1, TimeUnit.SECONDS);
        simpleQuikListener.setCheckConnectedPeriod(1, TimeUnit.SECONDS);
        simpleQuikListener.setExceptionPauseDuration(15, TimeUnit.SECONDS);
        simpleQuikListener.setSubscriptionPeriod(10, TimeUnit.SECONDS);
        simpleQuikListener.setOnlineDuration(15, TimeUnit.SECONDS);

        simpleQuikListener.setLogger(logger);
        simpleQuikListener.setLogPrefix(SimpleQuikListenerTest.class.getSimpleName() + ": ");

        simpleQuikListener.addCallbackSubscription("OnTrade", "*");
        simpleQuikListener.addCallbackSubscription("OnOrder", "*");
//        simpleQuikListener.addCallbackSubscription("OnAllTrade", "*");

        simpleQuikListener.addSecurityParameters(ClassSecCode.of("TQBR", "SBER"), List.of("LAST", "BID", "OFFER"));
        simpleQuikListener.addSecurityParameters(ClassSecCode.of("TQBR", "GAZP"), List.of("LAST", "BID", "OFFER"));
        simpleQuikListener.addSecurityParameters(ClassSecCode.of("TQBR", "LKOH"), List.of("LAST", "BID", "OFFER"));

        simpleQuikListener.addSecurityCandles(ClassSecCode.of("TQBR", "SBER"), List.of(1, 5, 10, 1440));
        simpleQuikListener.addSecurityCandles(ClassSecCode.of("TQBR", "GAZP"), List.of(5, 15, 60));
        simpleQuikListener.addSecurityCandles(ClassSecCode.of("TQBR", "LKOH"), List.of(30));

        final QuikConnect quikConnect = new QuikConnect(
                "localhost",
                10001,
                10002,
                SimpleQuikListenerTest.class.getSimpleName(),
                simpleQuikListener);
        simpleQuikListener.setQuikConnect(quikConnect);

        quikConnect.start();

        final ZonedDateTime stoppingTime = ZonedDateTime.now().plus(5, ChronoUnit.MINUTES);
        while (ZonedDateTime.now().isBefore(stoppingTime) && !Thread.currentThread().isInterrupted()) {
            processRunnables(simpleQuikListener.queue);
            simpleQuikListener.ensureConnection();
            simpleQuikListener.ensureSubscription();
            try {
                //noinspection BusyWait
                Thread.sleep(100L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        quikConnect.shutdown();
        processRunnables(simpleQuikListener.queue);
    }

    private void processRunnables(final Queue<Runnable> queue) {
        Runnable runnable;
        while ((runnable = queue.poll()) != null) {
            try {
                runnable.run();
            } catch (final Exception e) {
                logger.log(AbstractLogger.ERROR, "Cannot execute a runnable from QuikListener", e);
            }
        }
    }
}

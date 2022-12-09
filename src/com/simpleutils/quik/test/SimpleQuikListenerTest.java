package com.simpleutils.quik.test;

import com.simpleutils.logs.AbstractLogger;
import com.simpleutils.logs.SimpleLogger;
import com.simpleutils.quik.ClassSecCode;
import com.simpleutils.quik.QuikConnect;
import com.simpleutils.quik.QuikListener;
import com.simpleutils.quik.SimpleQuikListener;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class SimpleQuikListenerTest {

    private final AbstractLogger logger = new SimpleLogger();

    public static void main(final String[] args) {
        new SimpleQuikListenerTest().test();
    }

    private void test() {
        logger.withLogLevel(AbstractLogger.TRACE);

        final SimpleQuikListener simpleQuikListener = new SimpleQuikListener();

        simpleQuikListener.setRequestTimeout(Duration.of(1, ChronoUnit.SECONDS));
        simpleQuikListener.setCheckConnectedPeriod(Duration.of(1, ChronoUnit.SECONDS));
        simpleQuikListener.setPauseAfterException(Duration.of(15, ChronoUnit.SECONDS));
        simpleQuikListener.setSubscriptionPeriod(Duration.of(10, ChronoUnit.SECONDS));
        simpleQuikListener.setOnlineDuration(Duration.of(15, ChronoUnit.SECONDS));

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

        simpleQuikListener.addLevel2Quotes(ClassSecCode.of("TQBR", "SBER"));
        simpleQuikListener.addLevel2Quotes(ClassSecCode.of("TQBR", "GAZP"));
        simpleQuikListener.addLevel2Quotes(ClassSecCode.of("TQBR", "LKOH"));

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
            processRunnables(simpleQuikListener);
            simpleQuikListener.ensureConnection();
            simpleQuikListener.ensureSubscription();
            if (ZonedDateTime.now().getSecond() == 0) {
                logger.debug("Force subscription");
                simpleQuikListener.subscribe();
                try {
                    //noinspection BusyWait
                    Thread.sleep(1000L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                //noinspection BusyWait
                Thread.sleep(100L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        quikConnect.shutdown();
        processRunnables(simpleQuikListener);
    }

    private void processRunnables(final QuikListener quikListener) {
        Runnable runnable;
        while ((runnable = quikListener.poll()) != null) {
            try {
                runnable.run();
            } catch (final Exception e) {
                logger.log(AbstractLogger.ERROR, "Cannot execute a runnable from "
                        + quikListener.getClass().getSimpleName(), e);
            }
        }
    }
}

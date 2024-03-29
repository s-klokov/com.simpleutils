package com.simpleutils.quik.test;

import com.simpleutils.logs.AbstractLogger;
import com.simpleutils.logs.SimpleLogger;
import com.simpleutils.quik.AbstractQuikListener;
import com.simpleutils.quik.QuikConnect;
import com.simpleutils.quik.QuikListener;
import org.json.simple.JSONObject;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Тестирование подключения к терминалу QUIK.
 */
class QuikConnectTest {

    private static final AbstractLogger LOGGER = new SimpleLogger();
    private QuikConnect quikConnect = null;
    private QuikListener quikListener = null;

    public static void main(final String[] args) {
        LOGGER.info("STARTED");
        final QuikConnectTest test = new QuikConnectTest();
        test.init();
        test.run();
        test.shutdown();
        LOGGER.info("SHUTDOWN");
    }

    private void init() {
        quikListener = new TestListener();
        quikConnect = new QuikConnect("localhost", 10001, 10002, "test", quikListener);
        quikListener.setQuikConnect(quikConnect);
    }

    private void run() {
        LOGGER.info("Starting QuikConnect");
        quikConnect.start();
        LOGGER.info("Starting execution thread");
        quikListener.getExecutionThread().start();
        runUntil(ZonedDateTime.now().plusSeconds(180));
    }

    private void shutdown() {
        LOGGER.info("Shutting down execution thread");
        quikListener.getExecutionThread().interrupt();
        try {
            quikListener.getExecutionThread().join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Shutting down QuikConnect");
        quikConnect.shutdown();
    }

    private void runUntil(final ZonedDateTime deadline) {
        while (deadline.isAfter(ZonedDateTime.now())) {
            try {
                //noinspection BusyWait
                Thread.sleep(100L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    static class TestListener extends AbstractQuikListener {
        private boolean areRequestsDone = false;
        private volatile boolean isOpen = false;

        TestListener() {
            executionThread = new Thread() {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        processRunnables();
                        if (isOpen && !areRequestsDone && !quikConnect.hasErrorMN() && !quikConnect.hasErrorCB()) {
                            doRequests(quikConnect);
                            areRequestsDone = true;
                        }
                        try {
                            //noinspection BusyWait
                            Thread.sleep(1L);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    LOGGER.info("Execution thread is done");
                }

                private void processRunnables() {
                    Runnable runnable;
                    while ((runnable = poll()) != null) {
                        try {
                            runnable.run();
                        } catch (final Exception e) {
                            LOGGER.log(AbstractLogger.ERROR, e.getMessage(), e);
                        }
                    }
                }

                private void doRequests(final QuikConnect quikConnect) {
                    try {
                        LOGGER.info("Correct requests:");
                        request(() -> quikConnect.executeMN("message(\"Hello, QLua-world!\", 2)", 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeCB("return os.sysdate()", 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeMN("os.sysdate", null, 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeCB("os.sysdate", List.of(), 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeMN("isConnected", null, 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeMN("math.max", List.of(1, 3, 5, 7), 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeMN("message", List.of("Hi, there!", 1), 5, TimeUnit.SECONDS));

                        LOGGER.info("Erroneous requests:");
                        request(() -> quikConnect.executeMN("return string(((", 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeMN("return math.max(\"ABC\", 15)", 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeMN("mess", null, 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeMN("math.max", List.of("ABC", 15), 5, TimeUnit.SECONDS));

                        LOGGER.info("Correct requests:");
                        request(() -> quikConnect.executeMN("return initDataSource(\"TQBR\", \"AFLT\", 1)", 5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeCB("OnAllTrade",
                                "function(t) return t.class_code == \"TQBR\" and t.sec_code == \"AFLT\" end",
                                5, TimeUnit.SECONDS));
                        request(() -> quikConnect.executeCB("OnCandle",
                                "function(t) return t.class_code == \"TQBR\" and t.sec_code == \"AFLT\" end",
                                5, TimeUnit.SECONDS));
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (final Exception e) {
                        LOGGER.log(AbstractLogger.ERROR, e.getMessage(), e);
                    }
                }

                private static void request(final Callable<JSONObject> callable) throws Exception {
                    long t = System.nanoTime();
                    final JSONObject json = callable.call();
                    t = (System.nanoTime() - t) / 1_000_000L;
                    LOGGER.info(t + " ms; " + json);
                }
            };
            executionThread.setName("ExecutionThread");
        }

        @Override
        public void onOpen() {
            LOGGER.info("onOpen");
            isOpen = true;
            submit(() -> LOGGER.info("onOpen"));
        }

        @Override
        public void onClose() {
            LOGGER.info("onClose");
            isOpen = false;
            submit(() -> LOGGER.info("onClose"));
        }

        @Override
        public void onCallback(final JSONObject jsonObject) {
            LOGGER.info("onCallBack " + jsonObject.get("callback"));
            LOGGER.info(jsonObject.toString());
        }

        @Override
        public void onExceptionMN(final Exception exception) {
            LOGGER.log(AbstractLogger.ERROR, "onExceptionMN", exception);
        }

        @Override
        public void onExceptionCB(final Exception exception) {
            LOGGER.log(AbstractLogger.ERROR, "onExceptionCB", exception);
        }
    }
}

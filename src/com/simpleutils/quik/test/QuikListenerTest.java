package com.simpleutils.quik.test;

import com.simpleutils.logs.AbstractLogger;
import com.simpleutils.logs.SimpleLogger;
import com.simpleutils.quik.AbstractQuikListener;
import com.simpleutils.quik.QuikConnect;
import org.json.simple.JSONObject;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

class QuikListenerTest extends AbstractQuikListener {

    private static final AbstractLogger LOGGER = new SimpleLogger();
    private boolean isOpen = false;

    public QuikListenerTest() {
        executionThread = Thread.currentThread();
    }

    @Override
    public void onOpen() {
        submit(() -> {
            LOGGER.debug("onOpen");
            isOpen = true;
        });
    }

    @Override
    public void onClose() {
        submit(() -> {
            LOGGER.debug("onClose");
            isOpen = false;
        });
    }

    @Override
    public void onCallback(final JSONObject jsonObject) {
        submit(() -> {
            LOGGER.debug("onCallback " + jsonObject.get("callback"));
            LOGGER.debug(jsonObject.toString());
        });
    }

    @Override
    public void onExceptionMN(final Exception exception) {
        submit(() -> LOGGER.log(AbstractLogger.ERROR, "onExceptionMN", exception));
    }

    @Override
    public void onExceptionCB(final Exception exception) {
        submit(() -> LOGGER.log(AbstractLogger.ERROR, "onExceptionCB", exception));
    }

    private void processRunnables() {
        Runnable runnable;
        while ((runnable = poll()) != null) {
            try {
                runnable.run();
            } catch (final Exception e) {
                LOGGER.log(AbstractLogger.ERROR, "Cannot execute a runnable from QuikListener", e);
            }
        }
    }

    @SuppressWarnings("BusyWait")
    public static void main(final String[] args) {
        final QuikListenerTest quikListenerTest = new QuikListenerTest();
        final QuikConnect quikConnect = new QuikConnect("127.0.0.1", 10001, 10002, "test", quikListenerTest);
        quikListenerTest.setQuikConnect(quikConnect);
        quikConnect.start();
        final ZonedDateTime stoppingTime = ZonedDateTime.now().plusMinutes(1);
        int counter = 0;
        final StringBuilder sb = new StringBuilder();
        while (ZonedDateTime.now().isBefore(stoppingTime)) {
            quikListenerTest.processRunnables();
            if (quikListenerTest.isOpen) {
                sb.setLength(0);
                sb.append(++counter).append(": ");
                try {
                    JSONObject json;
                    json = quikConnect.executeCB("isConnected", (List<?>) null, 5, TimeUnit.SECONDS);
                    sb.append(json.get("result")).append("; ");
                    json = quikConnect.executeMN("initDataSource", List.of("TQBR", "SBER", 5), 5, TimeUnit.SECONDS);
                    sb.append(json.get("result")).append("; ");
                    json = quikConnect.executeCB("getDataSourceSize", List.of("TQBR", "SBER", 5), 5, TimeUnit.SECONDS);
                    sb.append(json.get("result")).append("; ");
                    json = quikConnect.executeMN("getCandles", List.of("TQBR", "SBER", 5, 3), 5, TimeUnit.SECONDS);
                    sb.append(json.get("result")).append("; ");
                    json = quikConnect.executeCB("OnAllTrade", "*", 5, TimeUnit.SECONDS);
                    sb.append(json.get("result")).append("; ");
                    LOGGER.info(sb.toString());
                    Thread.sleep(250L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    LOGGER.log(AbstractLogger.ERROR, "Exception", e);
                    try {
                        Thread.sleep(1000L);
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } else {
                try {
                    Thread.sleep(1000L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        quikConnect.shutdown();
        quikListenerTest.processRunnables();
    }
}

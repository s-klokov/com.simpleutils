package com.simpleutils.quik;

import com.simpleutils.logs.AbstractLogger;
import org.json.simple.JSONObject;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SimpleQuikListener extends AbstractQuikListener {

    protected Duration requestTimeout = Duration.of(5, ChronoUnit.SECONDS);
    protected Duration pauseAfterException = Duration.of(65, ChronoUnit.SECONDS);
    protected Duration checkConnectedPeriod = Duration.of(5, ChronoUnit.SECONDS);
    protected Duration subscriptionPeriod = Duration.of(15, ChronoUnit.SECONDS);
    protected Duration onlineDuration = Duration.of(30, ChronoUnit.SECONDS);

    protected AbstractLogger logger = null;
    protected String logPrefix = "";
    protected Map<String, String> callbackSubscriptionMap = new LinkedHashMap<>();

    protected boolean isOpen = false;
    protected ZonedDateTime connectedSince = null;
    protected ZonedDateTime nextCheckConnectionTime = null;
    protected boolean isSubscribed = false;
    protected ZonedDateTime nextSubscriptionTime = null;

    public SimpleQuikListener() {
        executionThread = Thread.currentThread();
        callbackSubscriptionMap.put("OnDisconnected", "*");
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(final Duration duration) {
        requestTimeout = duration;
    }

    public void setPauseAfterException(final Duration duration) {
        pauseAfterException = duration;
    }

    public void setCheckConnectedPeriod(final Duration duration) {
        checkConnectedPeriod = duration;
    }

    public void setSubscriptionPeriod(final Duration duration) {
        subscriptionPeriod = duration;
    }

    public void setOnlineDuration(final Duration duration) {
        onlineDuration = duration;
    }

    public void setLogger(final AbstractLogger logger) {
        this.logger = logger;
    }

    public void setLogPrefix(final String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public void addCallbackSubscription(final String callback, final String filter) {
        callbackSubscriptionMap.put(callback, filter);
    }

    public boolean isOpen() {
        return isOpen;
    }

    public ZonedDateTime connectedSince() {
        return connectedSince;
    }

    public boolean isConnected() {
        return connectedSince != null;
    }

    public boolean isSubscribed() {
        return isSubscribed;
    }

    public boolean isOnline() {
        return isOpen && isSubscribed && connectedSince != null
                && ZonedDateTime.now().isAfter(connectedSince.plus(onlineDuration));
    }

    public boolean isSynchronized() throws ExecutionException, InterruptedException {
        return isConnected()
                && Boolean.TRUE.equals(quikConnect.executeMN(
                "ServerInfo.isSynchronized", null,
                getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS).get("result"));
    }

    public void logError(final String message, final Throwable t) {
        logger.log(AbstractLogger.ERROR, logPrefix + message, t);
    }

    @Override
    public void onOpen() {
        submit(() -> {
            if (logger != null) {
                logger.debug(() -> logPrefix + "onOpen");
            }
            final ZonedDateTime now = ZonedDateTime.now();
            isOpen = true;
            connectedSince = null;
            nextCheckConnectionTime = now;
            isSubscribed = false;
            nextSubscriptionTime = now;
        });
    }

    @Override
    public void onClose() {
        submit(() -> {
            if (logger != null) {
                logger.debug(() -> logPrefix + "onClose");
            }
            isOpen = false;
            connectedSince = null;
            nextCheckConnectionTime = null;
            isSubscribed = false;
            nextSubscriptionTime = null;
        });
    }

    @Override
    public void onCallback(final JSONObject jsonObject) {
        final String callback = (String) jsonObject.get("callback");
        submit(() -> {
            if (logger != null) {
                logger.trace(() -> logPrefix + "onCallback " + callback);
                logger.trace(() -> logPrefix + jsonObject);
            }
            processCallback(callback, jsonObject);
        });
    }

    /**
     * Для реакции на коллбэки рекомендуется перегружать этот метод в потомках.
     *
     * @param callback   название коллбэка
     * @param jsonObject JSON-объект с информацией о коллбэке
     */
    protected void processCallback(final String callback, final JSONObject jsonObject) {
        switch (callback) {
            case "OnConnected" -> onConnected();
            case "OnDisconnected" -> onDisconnected();
            default -> onUnknownCallback(callback);
        }
    }

    @Override
    public void onExceptionMN(final Exception exception) {
        submit(() -> {
            if (logger != null) {
                logger.log(AbstractLogger.ERROR, logPrefix + "onExceptionMN", exception);
            }
            scheduleRecovery();
        });
    }

    @Override
    public void onExceptionCB(final Exception exception) {
        submit(() -> {
            if (logger != null) {
                logger.log(AbstractLogger.ERROR, logPrefix + "onExceptionCB", exception);
            }
            scheduleRecovery();
        });
    }

    protected void scheduleRecovery() {
        final ZonedDateTime zdt = ZonedDateTime.now().plus(pauseAfterException);
        connectedSince = null;
        nextCheckConnectionTime = zdt;
        isSubscribed = false;
        nextSubscriptionTime = zdt;
    }

    /**
     * Регулярный вызов этого метода отслеживает подключение к терминалу QUIK.
     */
    public void ensureConnection() {
        if (isOpen && nextCheckConnectionTime != null && ZonedDateTime.now().isAfter(nextCheckConnectionTime)) {
            try {
                final JSONObject response = quikConnect.executeMN(
                        "isConnected", null,
                        requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (response.get("result").equals(1L)) {
                    onConnected();
                } else {
                    onDisconnected();
                }
            } catch (final Exception e) {
                onDisconnected();
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Регулярный вызов этого метода реализует подписку, возможно повторную, на необходимые коллбэки и рыночные данные.
     */
    public void ensureSubscription() {
        if (isOpen && !isSubscribed && nextSubscriptionTime != null && ZonedDateTime.now().isAfter(nextSubscriptionTime)) {
            subscribe();
        }
    }

    /**
     * Метод для подписки на необходимые коллбэки и рыночные данные.
     */
    public void subscribe() {
        try {
            subscribeToCallbacks();
            isSubscribed = true;
        } catch (final Exception e) {
            isSubscribed = false;
            nextSubscriptionTime = ZonedDateTime.now().plus(subscriptionPeriod);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void subscribeToCallbacks() throws ExecutionException, InterruptedException {
        for (final Map.Entry<String, String> entry : callbackSubscriptionMap.entrySet()) {
            subscribeToCallback(entry.getKey(), entry.getValue());
        }
    }

    private void subscribeToCallback(final String callback, final String filter) throws ExecutionException, InterruptedException {
        final JSONObject response = quikConnect.executeCB(
                callback,
                filter,
                requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(response.get("status"))) {
            if (logger != null) {
                logger.debug(() -> logPrefix + "Subscribed to callback " + callback + ".");
            }
        } else {
            final String message = "Cannot subscribe to callback " + callback + ".";
            if (logger != null) {
                logger.error(logPrefix + message);
            }
            throw new RuntimeException(message);
        }
    }

    protected void onConnected() {
        final ZonedDateTime now = ZonedDateTime.now();
        if (connectedSince == null) {
            connectedSince = now;
            if (logger != null) {
                logger.debug(() -> logPrefix + "Quik is connected.");
            }
        }
        if (logger != null) {
            logger.trace(() -> logPrefix + "isOnline: " + isOnline() + ", connectedSince: " + connectedSince());
        }
        nextCheckConnectionTime = now.plus(checkConnectedPeriod);
    }

    protected void onDisconnected() {
        if (connectedSince != null) {
            connectedSince = null;
            if (logger != null) {
                logger.debug(() -> logPrefix + "Quik is disconnected.");
            }
        }
        nextCheckConnectionTime = ZonedDateTime.now().plus(checkConnectedPeriod);
    }

    protected void onUnknownCallback(final String callback) {
        logger.debug(() -> logPrefix + "Unknown callback: " + callback);
    }
}

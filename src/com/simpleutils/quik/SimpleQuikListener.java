package com.simpleutils.quik;

import com.simpleutils.logs.AbstractLogger;
import com.simpleutils.quik.requests.BulkLevel2QuotesSubscriptionRequest;
import com.simpleutils.quik.requests.CandlesSubscriptionRequest;
import com.simpleutils.quik.requests.ParamSubscriptionRequest;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
    protected Map<ClassSecCode, Set<String>> securityParametersMap = new LinkedHashMap<>();
    protected Map<ClassSecCode, Set<Integer>> securityCandlesMap = new LinkedHashMap<>();
    protected Set<ClassSecCode> level2QuotesSet = new LinkedHashSet<>();

    protected boolean isOpen = false;
    protected ZonedDateTime connectedSince = null;
    protected ZonedDateTime nextCheckConnectionTime = null;
    protected boolean isSubscribed = false;
    protected ZonedDateTime nextSubscriptionTime = null;

    public SimpleQuikListener() {
        executionThread = Thread.currentThread();
        callbackSubscriptionMap.put("OnDisconnected", "*");
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

    public void addSecurityParameters(final ClassSecCode classSecCode, final Collection<String> parameters) {
        final Set<String> set = securityParametersMap.get(classSecCode);
        if (set == null) {
            securityParametersMap.put(classSecCode, new LinkedHashSet<>(parameters));
        } else {
            set.addAll(parameters);
        }
    }

    public void addSecurityCandles(final ClassSecCode classSecCode, final Collection<Integer> intervals) {
        final Set<Integer> set = securityCandlesMap.get(classSecCode);
        if (set == null) {
            securityCandlesMap.put(classSecCode, new LinkedHashSet<>(intervals));
        } else {
            set.addAll(intervals);
        }
    }

    public void addLevel2Quotes(final ClassSecCode classSecCode) {
        level2QuotesSet.add(classSecCode);
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

    @Override
    public void onOpen() {
        queue.add(() -> {
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
        queue.add(() -> {
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
        queue.add(() -> {
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
     * @param jsonObject JSON-объек с информацией о коллбэке
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
        queue.add(() -> {
            if (logger != null) {
                logger.log(AbstractLogger.ERROR, logPrefix + "onExceptionMN", exception);
            }
            scheduleRecovery();
        });
    }

    @Override
    public void onExceptionCB(final Exception exception) {
        queue.add(() -> {
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
            subscribeToParameters();
            subscribeToCandles();
            subscribeToLevel2Quotes();
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

    private void subscribeToParameters() throws ExecutionException, InterruptedException {
        for (final Map.Entry<ClassSecCode, Set<String>> entry : securityParametersMap.entrySet()) {
            subscribeToSecurityParameters(entry.getKey(), entry.getValue());
        }
    }

    private void subscribeToCandles() throws ExecutionException, InterruptedException {
        for (final Map.Entry<ClassSecCode, Set<Integer>> entry : securityCandlesMap.entrySet()) {
            subscribeToSecurityCandles(entry.getKey(), entry.getValue());
        }
    }

    private void subscribeToLevel2Quotes() throws ExecutionException, InterruptedException {
        if (level2QuotesSet.isEmpty()) {
            return;
        }
        final JSONObject response = quikConnect.executeMN(
                new BulkLevel2QuotesSubscriptionRequest(level2QuotesSet).getRequest(),
                requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        final JSONArray result = (JSONArray) response.get("result");
        String errorMessage = null;
        for (final Object o : result) {
            final JSONObject json = (JSONObject) o;
            if (Boolean.TRUE.equals(json.get("subscribed"))) {
                if (logger != null) {
                    logger.debug(() -> logPrefix + "Subscribed to Level2 quotes for " + json.get("classCode") + ":" + json.get("secCode") + ".");
                }
            } else {
                errorMessage = "Cannot subscribed to Level2 quotes for " + json.get("classCode") + ":" + json.get("secCode") + ".";
                if (logger != null) {
                    logger.debug(logPrefix + errorMessage);
                }
            }
        }
        if (errorMessage != null) {
            throw new RuntimeException(errorMessage);
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

    private void subscribeToSecurityParameters(final ClassSecCode classSecCode,
                                               final Collection<String> parameters) throws ExecutionException, InterruptedException {
        final JSONObject response = quikConnect.executeMN(
                new ParamSubscriptionRequest(classSecCode, parameters).getRequest(),
                requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(response.get("result"))) {
            if (logger != null) {
                logger.debug(() -> logPrefix + "Subscribed to " + classSecCode + " " + parameters + ".");
            }
            return;
        }
        final String message = "Cannot subscribe to " + classSecCode + " parameters " + parameters + ".";
        if (logger != null) {
            logger.error(logPrefix + message);
            throw new RuntimeException(message);
        }
    }

    private void subscribeToSecurityCandles(final ClassSecCode classSecCode,
                                            final Collection<Integer> intervals) throws ExecutionException, InterruptedException {
        final JSONObject response = quikConnect.executeMN(
                new CandlesSubscriptionRequest(classSecCode, intervals).getRequest(),
                requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            final JSONObject result = (JSONObject) response.get("result");
            RuntimeException runtimeException = null;
            for (final int interval : intervals) {
                final String key = String.valueOf(interval);
                if (!"ok".equals(result.get(key))) {
                    final String message = "Cannot subscribe to " + classSecCode
                            + " candles for interval " + interval + ": " + result.get(key);
                    if (logger != null) {
                        logger.error(logPrefix + message);
                        runtimeException = new RuntimeException(message);
                    }
                }
            }
            if (runtimeException == null) {
                if (logger != null) {
                    logger.debug(() -> logPrefix + "Subscribed to " + classSecCode + " candles for intervals " + intervals + ".");
                }
            } else {
                throw runtimeException;
            }
        } catch (final NullPointerException | ClassCastException e) {
            final String message = "Cannot subscribe to " + classSecCode + " candles for intervals " + intervals + ".";
            if (logger != null) {
                logger.error(logPrefix + message);
                throw new RuntimeException(message);
            }
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
    }
}

package com.simpleutils.quik;

import com.simpleutils.logs.AbstractLogger;
import com.simpleutils.quik.requests.CandlesSubscriptionRequest;
import com.simpleutils.quik.requests.ParamSubscriptionRequest;
import org.json.simple.JSONObject;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SimpleQuikListener extends AbstractQuikListener {

    protected long requestTimeoutMillis = 5_000L;
    protected long exceptionPauseDurationMillis = 65_000L;
    protected long checkConnectedPeriodMillis = 5_000L;
    protected long subscriptionPeriodMillis = 15_000L;
    protected long onlineDurationMillis = 30_000L;

    protected AbstractLogger logger = null;
    protected String logPrefix = "";
    protected Map<String, String> callbackSubscriptionMap = new LinkedHashMap<>();
    protected Map<ClassSecCode, Set<String>> securityParametersMap = new LinkedHashMap<>();
    protected Map<ClassSecCode, Set<Integer>> securityCandlesMap = new LinkedHashMap<>();

    protected boolean isOpen = false;
    protected ZonedDateTime connectedSince = null;
    protected ZonedDateTime nextCheckConnectionTime = null;
    protected boolean isSubscribed = false;
    protected ZonedDateTime nextSubscriptionTime = null;

    public final Map<Long, JSONObject> transIdToTransReplyMap = new HashMap<>();

    public SimpleQuikListener() {
        executionThread = Thread.currentThread();
        callbackSubscriptionMap.put("OnDisconnected", "*");
    }

    public void setRequestTimeout(final long duration, final TimeUnit unit) {
        requestTimeoutMillis = unit.toMillis(duration);
    }

    public void setExceptionPauseDuration(final long duration, final TimeUnit unit) {
        exceptionPauseDurationMillis = unit.toMillis(duration);
    }

    public void setCheckConnectedPeriod(final long duration, final TimeUnit unit) {
        checkConnectedPeriodMillis = unit.toMillis(duration);
    }

    public void setSubscriptionPeriod(final long duration, final TimeUnit unit) {
        subscriptionPeriodMillis = unit.toMillis(duration);
    }

    public void setOnlineDuration(final long duration, final TimeUnit unit) {
        onlineDurationMillis = unit.toMillis(duration);
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
                && ZonedDateTime.now().isAfter(connectedSince.plus(onlineDurationMillis, ChronoUnit.MILLIS));
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
                logger.debug(() -> logPrefix + "onCallback " + callback);
                logger.trace(() -> logPrefix + jsonObject);
            }
            if ("OnDisconnected".equals(callback)) {
                onDisconnected();
            }
            if ("OnTransReply".equals(callback)) {
                onTransReply((JSONObject) jsonObject.get("arg1"));
            }
        });
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
        final ZonedDateTime zdt = ZonedDateTime.now().plus(exceptionPauseDurationMillis, ChronoUnit.MILLIS);
        connectedSince = null;
        nextCheckConnectionTime = zdt;
        isSubscribed = false;
        nextSubscriptionTime = zdt;
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
        nextCheckConnectionTime = now.plus(checkConnectedPeriodMillis, ChronoUnit.MILLIS);
    }

    protected void onDisconnected() {
        if (connectedSince != null) {
            connectedSince = null;
            if (logger != null) {
                logger.debug(() -> logPrefix + "Quik is disconnected.");
            }
        }
        nextCheckConnectionTime = ZonedDateTime.now().plus(checkConnectedPeriodMillis, ChronoUnit.MILLIS);
    }

    public void ensureConnection() {
        if (isOpen && nextCheckConnectionTime != null && ZonedDateTime.now().isAfter(nextCheckConnectionTime)) {
            try {
                final JSONObject response = quikConnect.executeMN(
                        "isConnected", null,
                        requestTimeoutMillis, TimeUnit.MILLISECONDS);
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

    public void ensureSubscription() {
        if (isOpen && !isSubscribed && nextSubscriptionTime != null && ZonedDateTime.now().isAfter(nextSubscriptionTime)) {
            try {
                subscribeToCallbacks();
                subscribeToParameters();
                subscribeToCandles();
                isSubscribed = true;
            } catch (final Exception e) {
                isSubscribed = false;
                nextSubscriptionTime = ZonedDateTime.now().plus(subscriptionPeriodMillis, ChronoUnit.MILLIS);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
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

    private void subscribeToCallback(final String callback, final String filter) throws ExecutionException, InterruptedException {
        final JSONObject response = quikConnect.executeCB(
                callback,
                filter,
                requestTimeoutMillis, TimeUnit.MILLISECONDS);
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
                new ParamSubscriptionRequest(classSecCode.classCode(), classSecCode.secCode(), parameters).getRequest(),
                requestTimeoutMillis, TimeUnit.MILLISECONDS);
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
                new CandlesSubscriptionRequest(classSecCode.classCode(), classSecCode.secCode(), intervals).getRequest(),
                requestTimeoutMillis, TimeUnit.MILLISECONDS);
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

    protected void onTransReply(final JSONObject tr) {
        try {
            final long transId = (long) tr.get("trans_id");
            if (transId != 0L) {
                transIdToTransReplyMap.put(transId, tr);
            }
        } catch (final NullPointerException | ClassCastException ignored) {
        }
    }
}

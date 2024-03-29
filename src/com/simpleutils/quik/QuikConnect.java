package com.simpleutils.quik;

import com.simpleutils.json.JSONConfig;
import com.simpleutils.socket.SocketConnector;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.json.simple.JSONArray.toJSONString;
import static org.json.simple.JSONValue.escape;

/**
 * Подключение к терминалу QUIK через сокеты.
 */
public class QuikConnect {
    /**
     * Таймаут в миллисекундах перед повторной попыткой открыть сокеты в случае возникновения ошибок.
     */
    public volatile Duration errorTimeout = Duration.of(60, ChronoUnit.SECONDS);
    /**
     * Периодичность отправки ping-сообщений в миллисекундах.
     */
    public volatile Duration pingTimeout = Duration.of(15, ChronoUnit.SECONDS);
    /**
     * Длительность паузы рабочего цикла в миллисекундах в случае отсутствия сообщений.
     */
    public volatile Duration idleSleepTimeout = Duration.of(10, ChronoUnit.MILLIS);
    /**
     * Длительность паузы рабочего цикла в миллисекундах в случае ошибок.
     */
    public volatile Duration errorSleepTimeout = Duration.of(100, ChronoUnit.MILLIS);
    /**
     * Надо ли сообщать об ошибках парсинга ответов слушателю.
     * Значение {@code false} может использовано для исправления багов,
     * когда из сокета приходят некорректные строки.
     */
    public volatile boolean raiseParseExceptionToListener = true;

    /**
     * Кодовая страница для текстовых сообщений.
     */
    private final Charset charset = Charset.forName("CP1251");
    /**
     * Счётчик для номеров сообщений.
     */
    private final AtomicLong counter = new AtomicLong();
    /**
     * Соответствие между номерами запросов и ответами на них.
     */
    private final Map<Long, CompletableFuture<JSONObject>> responseMap = new HashMap<>();
    /**
     * Socket-коннектор к MN-серверу.
     */
    private final SocketConnector scMN;
    /**
     * Socket-коннектор к CB-серверу.
     */
    private final SocketConnector scCB;
    /**
     * Идентификатор клиента.
     */
    public final String clientId;
    /**
     * Поток, который слушает ответы от терминала QUIK.
     */
    private final Thread listeningThread;

    /**
     * Парсер json-строк.
     */
    private final JSONParser parser = new JSONParser();
    /**
     * Признак того, что socket-коннекторы открыты.
     */
    private boolean hasOpenSocketConnectors = false;
    /**
     * Признак ошибки при взаимодействии с MN-сервером.
     */
    private volatile boolean hasErrorMN = false;
    /**
     * Признак ошибки при взаимодействии с CB-сервером.
     */
    private volatile boolean hasErrorCB = false;
    /**
     * Момент возникновения ошибки взаимодействия с терминалом.
     */
    private volatile ZonedDateTime errorTime = ZonedDateTime.now().minusYears(1);
    /**
     * Момент отправки сообщения "ping".
     */
    private ZonedDateTime lastPingTime = ZonedDateTime.now().minusYears(1);
    /**
     * Счётчик сообщений в одной итерации рабочего цикла.
     */
    private int count = 0;

    public static QuikConnect newInstance(final JSONObject config, final QuikListener quikListener) {
        return new QuikConnect(
                JSONConfig.getString(config, "host"),
                JSONConfig.getInt(config, "portMN"),
                JSONConfig.getInt(config, "portCB"),
                JSONConfig.getString(config, "clientId"),
                quikListener);
    }

    /**
     * Конструктор.
     *
     * @param host     хост
     * @param portMN   порт MN-сервера
     * @param portCB   порт CB-сервера
     * @param clientId идентификатор клиента
     */
    public QuikConnect(final String host, final int portMN, final int portCB,
                       final String clientId,
                       final QuikListener listener) {
        scMN = new SocketConnector(host, portMN);
        scCB = new SocketConnector(host, portCB);
        this.clientId = clientId;
        listeningThread = new Thread() {
            @Override
            public void run() {
                while (!interrupted()) {
                    step();
                }
                if (hasOpenSocketConnectors) {
                    closeSocketConnectors();
                }
            }

            private void step() {
                if (hasErrorMN || hasErrorCB) {
                    if (hasOpenSocketConnectors) {
                        closeSocketConnectors();
                    }
                    if (ZonedDateTime.now().isAfter(errorTime.plus(errorTimeout))) {
                        hasErrorMN = false;
                        hasErrorCB = false;
                    }
                }
                if (hasErrorMN || hasErrorCB) {
                    pause(errorSleepTimeout);
                    cleanupResponseMap();
                    return;
                }
                if (!hasOpenSocketConnectors) {
                    synchronized (scMN) {
                        try {
                            scMN.open(charset);
                        } catch (final IOException e) {
                            hasErrorMN = true;
                            errorTime = ZonedDateTime.now();
                            try {
                                listener.onExceptionMN(e);
                            } catch (final Exception ignored) {
                            }
                        }
                    }
                    synchronized (scCB) {
                        try {
                            scCB.open(charset);
                        } catch (final IOException e) {
                            hasErrorCB = true;
                            errorTime = ZonedDateTime.now();
                            try {
                                listener.onExceptionCB(e);
                            } catch (final Exception ignored) {
                            }
                        }
                    }
                    if (hasErrorMN || hasErrorCB) {
                        hasErrorMN = true;
                        hasErrorCB = true;
                        closeSocketConnectors();
                        cleanupResponseMap();
                        return;
                    }
                    hasOpenSocketConnectors = true;
                    try {
                        listener.onOpen();
                    } catch (final Exception ignored) {
                    }
                }

                ensurePing();

                count = 0;
                if (!hasErrorMN) {
                    receiveMN();
                }
                if (!hasErrorCB) {
                    receiveCB();
                }
                cleanupResponseMap();
                if (count == 0) {
                    pause(idleSleepTimeout);
                }
            }

            private void receiveMN() {
                while (true) {
                    final String s;
                    synchronized (scMN) {
                        try {
                            s = scMN.receive();
                        } catch (final IOException e) {
                            hasErrorMN = true;
                            errorTime = ZonedDateTime.now();
                            try {
                                listener.onExceptionMN(e);
                            } catch (final Exception ignored) {
                            }
                            break;
                        }
                    }
                    if (s == null) {
                        break;
                    }
                    count++;
                    if ("pong".equals(s)) {
                        continue;
                    }
                    try {
                        final JSONObject jsonObject = (JSONObject) parser.parse(s);
                        final Object o = jsonObject.get("id");
                        if (o instanceof Long) {
                            final CompletableFuture<JSONObject> response;
                            synchronized (responseMap) {
                                response = responseMap.remove(o);
                            }
                            if (response != null) {
                                response.complete(jsonObject);
                            }
                        }
                    } catch (final ParseException e) {
                        if (raiseParseExceptionToListener) {
                            try {
                                listener.onExceptionMN(e);
                            } catch (final Exception ignored) {
                            }
                        }
                    } catch (final ClassCastException e) {
                        try {
                            listener.onExceptionMN(e);
                        } catch (final Exception ignored) {
                        }
                    }
                }
            }

            private void receiveCB() {
                while (true) {
                    final String s;
                    synchronized (scCB) {
                        try {
                            s = scCB.receive();
                        } catch (final IOException e) {
                            hasErrorCB = true;
                            errorTime = ZonedDateTime.now();
                            try {
                                listener.onExceptionCB(e);
                            } catch (final Exception ignored) {
                            }
                            break;
                        }
                    }
                    if (s == null) {
                        break;
                    }
                    count++;
                    if ("pong".equals(s)) {
                        continue;
                    }
                    try {
                        final JSONObject jsonObject = (JSONObject) parser.parse(s);
                        if (jsonObject.get("callback") instanceof String) {
                            listener.onCallback(jsonObject);
                            continue;
                        }
                        final Object o = jsonObject.get("id");
                        if (o instanceof Long) {
                            final CompletableFuture<JSONObject> response;
                            synchronized (responseMap) {
                                response = responseMap.remove(o);
                            }
                            if (response != null) {
                                response.complete(jsonObject);
                            }
                        }
                    } catch (final ParseException e) {
                        if (raiseParseExceptionToListener) {
                            try {
                                listener.onExceptionCB(e);
                            } catch (final Exception ignored) {
                            }
                        }
                    } catch (final ClassCastException e) {
                        try {
                            listener.onExceptionCB(e);
                        } catch (final Exception ignored) {
                        }
                    }
                }
            }

            private void cleanupResponseMap() {
                synchronized (responseMap) {
                    responseMap.entrySet().removeIf(entry -> entry.getValue().isDone());
                }
            }

            private void pause(final Duration duration) {
                try {
                    Thread.sleep(duration.toMillis());
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            private void ensurePing() {
                final ZonedDateTime now = ZonedDateTime.now();
                if (now.isAfter(lastPingTime.plus(pingTimeout))) {
                    synchronized (scMN) {
                        if (!hasErrorMN) {
                            try {
                                scMN.send("ping");
                            } catch (final IOException e) {
                                hasErrorMN = true;
                                errorTime = ZonedDateTime.now();
                            }
                        }
                    }
                    synchronized (scCB) {
                        if (!hasErrorCB) {
                            try {
                                scCB.send("ping");
                            } catch (final IOException e) {
                                hasErrorCB = true;
                                errorTime = ZonedDateTime.now();
                            }
                        }
                    }
                    lastPingTime = now;
                }
            }

            private void closeSocketConnectors() {
                synchronized (scMN) {
                    try {
                        scMN.send("quit");
                    } catch (final IOException ignored) {
                    } finally {
                        scMN.close();
                    }
                }
                synchronized (scCB) {
                    try {
                        scCB.send("quit");
                    } catch (final IOException ignored) {
                    } finally {
                        scCB.close();
                    }
                }
                hasOpenSocketConnectors = false;
                try {
                    listener.onClose();
                } catch (final Exception ignored) {
                }
            }
        };
        listeningThread.setName(clientId + "-" + QuikConnect.class.getSimpleName());
    }

    /**
     * Запустить подключение к терминалу QUIK.
     * <p>
     * Порядок вызова:<br>
     * 1) {@link QuikListener#setQuikConnect(QuikConnect)};<br>
     * 2) этот метод;<br>
     * 3) поток слушателя: {@code listener.getExecutionThread().start()}.
     */
    public void start() {
        listeningThread.start();
    }

    public boolean hasErrorMN() {
        return hasErrorMN;
    }

    public boolean hasErrorCB() {
        return hasErrorCB;
    }

    /**
     * Остановить подключение к терминалу QUIK.
     * <p>
     * Порядок остановки:<br>
     * 1) поток, реализующий бизнес-логику слушателя:<br>
     * {@code listener.getExecutionThread().interrupt();}<br>
     * {@code listener.getExecutionThread().join();}<br>
     * 2) этот метод.
     */
    public void shutdown() {
        listeningThread.interrupt();
        try {
            listeningThread.join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendSyncMN(final String s) throws IOException {
        synchronized (scMN) {
            try {
                scMN.send(s);
            } catch (final IOException e) {
                hasErrorMN = true;
                errorTime = ZonedDateTime.now();
                throw e;
            }
        }
    }

    private void sendSyncCB(final String s) throws IOException {
        synchronized (scCB) {
            try {
                scCB.send(s);
            } catch (final IOException e) {
                hasErrorCB = true;
                errorTime = ZonedDateTime.now();
                throw e;
            }
        }
    }

    private long sendMN(final String chunk) throws IOException {
        final long id = counter.incrementAndGet();
        sendSyncMN("{\"id\":" + id
                + ",\"clientId\":\"" + clientId
                + "\",\"chunk\":\"" + escape(chunk) + "\"}");
        return id;
    }

    private long sendMN(final String fname, final List<?> args) throws IOException {
        final long id = counter.incrementAndGet();
        sendSyncMN("{\"id\":" + id
                + ",\"clientId\":\"" + clientId
                + "\",\"fname\":\"" + fname
                + "\",\"args\":" + toJSONString(args) + "}");
        return id;
    }

    private long sendCB(final String chunk) throws IOException {
        final long id = counter.incrementAndGet();
        sendSyncCB("{\"id\":" + id
                + ",\"clientId\":\"" + clientId
                + "\",\"chunk\":\"" + escape(chunk) + "\"}");

        return id;
    }

    private long sendCB(final String fname, final List<?> args) throws IOException {
        final long id = counter.incrementAndGet();
        sendSyncCB("{\"id\":" + id
                + ",\"clientId\":\"" + clientId
                + "\",\"fname\":\"" + fname
                + "\",\"args\":" + toJSONString(args) + "}");
        return id;
    }

    private long sendCB(final String callback, final String filter) throws IOException {
        final long id = counter.incrementAndGet();
        sendSyncCB("{\"id\":" + id
                + ",\"clientId\":\"" + clientId
                + "\",\"callback\":\"" + callback
                + "\",\"filter\":\"" + escape(filter) + "\"}");
        return id;
    }

    /**
     * Отправить chunk-запрос MN-серверу и получить будущий ответ.
     *
     * @param chunk   код запроса на языке QLua
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return будущий ответ MN-сервера
     */
    public CompletableFuture<JSONObject> submitMN(final String chunk,
                                                  final long timeout, final TimeUnit unit) {
        synchronized (responseMap) {
            final long id;
            try {
                id = sendMN(chunk);
            } catch (final IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            final CompletableFuture<JSONObject> response = new CompletableFuture<JSONObject>().orTimeout(timeout, unit);
            responseMap.put(id, response);
            return response;
        }
    }

    /**
     * Отправить function-запрос MN-серверу и получить будущий ответ.
     *
     * @param fname   имя QLua-функции
     * @param args    список аргументов функции
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return будущий ответ MN-сервера
     */
    public CompletableFuture<JSONObject> submitMN(final String fname, final List<?> args,
                                                  final long timeout, final TimeUnit unit) {
        synchronized (responseMap) {
            final long id;
            try {
                id = sendMN(fname, args);
            } catch (final IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            final CompletableFuture<JSONObject> response = new CompletableFuture<JSONObject>().orTimeout(timeout, unit);
            responseMap.put(id, response);
            return response;
        }
    }

    /**
     * Отправить chunk-запрос CB-серверу и получить будущий ответ.
     *
     * @param chunk   код запроса на языке QLua
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return будущий ответ CB-сервера
     */
    public CompletableFuture<JSONObject> submitCB(final String chunk,
                                                  final long timeout, final TimeUnit unit) {
        synchronized (responseMap) {
            final long id;
            try {
                id = sendCB(chunk);
            } catch (final IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            final CompletableFuture<JSONObject> response = new CompletableFuture<JSONObject>().orTimeout(timeout, unit);
            responseMap.put(id, response);
            return response;
        }
    }

    /**
     * Отправить function-запрос CB-серверу и получить будущий ответ.
     *
     * @param fname   имя QLua-функции
     * @param args    список аргументов функции
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return будущий ответ CB-сервера
     */
    public CompletableFuture<JSONObject> submitCB(final String fname, final List<?> args,
                                                  final long timeout, final TimeUnit unit) {
        synchronized (responseMap) {
            final long id;
            try {
                id = sendCB(fname, args);
            } catch (final IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            final CompletableFuture<JSONObject> response = new CompletableFuture<JSONObject>().orTimeout(timeout, unit);
            responseMap.put(id, response);
            return response;
        }
    }

    /**
     * Отправить запрос CB-серверу и получить будущий ответ.
     *
     * @param callback имя коллбэка
     * @param filter   код функции фильтрации на языке QLua
     * @param timeout  таймаут ожидания
     * @param unit     единица измерения времени
     * @return будущий ответ CB-сервера
     */
    public CompletableFuture<JSONObject> submitCB(final String callback, final String filter,
                                                  final long timeout, final TimeUnit unit) {
        synchronized (responseMap) {
            final long id;
            try {
                id = sendCB(callback, filter);
            } catch (final IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            final CompletableFuture<JSONObject> response = new CompletableFuture<JSONObject>().orTimeout(timeout, unit);
            responseMap.put(id, response);
            return response;
        }
    }

    /**
     * Отправить chunk-запрос MN-серверу и ждать получения ответа.
     *
     * @param chunk   код запроса на языке QLua
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return ответ MN-сервера
     */
    public JSONObject executeMN(final String chunk,
                                final long timeout, final TimeUnit unit) throws CancellationException, ExecutionException, InterruptedException {
        return submitMN(chunk, timeout, unit).get();
    }

    /**
     * Отправить function-запрос MN-серверу и ждать получения ответа.
     *
     * @param fname   имя QLua-функции
     * @param args    список аргументов функции
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return ответ MN-сервера
     */
    public JSONObject executeMN(final String fname, final List<?> args,
                                final long timeout, final TimeUnit unit) throws CancellationException, ExecutionException, InterruptedException {
        return submitMN(fname, args, timeout, unit).get();
    }

    /**
     * Отправить chunk-запрос CB-серверу и ждать получения ответа.
     *
     * @param chunk   код запроса на языке QLua
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return ответ CB-сервера
     */
    public JSONObject executeCB(final String chunk,
                                final long timeout, final TimeUnit unit) throws CancellationException, ExecutionException, InterruptedException {
        return submitCB(chunk, timeout, unit).get();
    }

    /**
     * Отправить function-запрос CB-серверу и ждать получения ответа.
     *
     * @param fname   имя QLua-функции
     * @param args    список аргументов функции
     * @param timeout таймаут ожидания
     * @param unit    единица измерения времени
     * @return ответ CB-сервера
     */
    public JSONObject executeCB(final String fname, final List<?> args,
                                final long timeout, final TimeUnit unit) throws CancellationException, ExecutionException, InterruptedException {
        return submitCB(fname, args, timeout, unit).get();
    }

    /**
     * Отправить запрос CB-серверу и ждать получения ответа.
     *
     * @param callback имя коллбэка
     * @param filter   код функции фильтрации на языке QLua
     * @param timeout  таймаут ожидания
     * @param unit     единица измерения времени
     * @return ответ CB-сервера
     */
    public JSONObject executeCB(final String callback, final String filter,
                                final long timeout, final TimeUnit unit) throws CancellationException, ExecutionException, InterruptedException {
        return submitCB(callback, filter, timeout, unit).get();
    }
}

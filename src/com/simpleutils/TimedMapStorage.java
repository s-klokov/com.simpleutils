package com.simpleutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Реализация хранилища соответствия K -> V с сохранением состояния в файл.
 * Каждой паре (key, value) сопоставляется момент времени в виде числа.
 * При записи всего соответствия в файл и чтении из файла возможно удаление
 * "старых" элементов. Также можно дописывать информацию об отдельной паре
 * в конец файла; при этом при последующем чтении пара (key, value)
 * с повторно встречающемся ключе key приведёт к замене значения value.
 */
public abstract class TimedMapStorage<K, V> {
    /**
     * Соответствие.
     */
    protected final Map<K, V> map;
    /**
     * Имя файла для сохранения.
     */
    protected final String fileName;

    /**
     * Конструктор.
     *
     * @param map      соответствие
     * @param fileName имя файла для сохранения
     */
    public TimedMapStorage(final Map<K, V> map, final String fileName) {
        this.map = map;
        this.fileName = fileName;
    }

    /**
     * Время в виде числа, рассчитанное на основе информации (ключ, значение).
     *
     * @param key   ключ
     * @param value значение
     * @return время в виде числа
     */
    protected abstract long getTime(K key, V value);

    /**
     * Представить ключ в виде строки, не содержащей символа перевода строки.
     *
     * @param key ключ
     * @return ключ в виде строки
     */
    protected abstract String encodeKey(final K key);

    /**
     * Представить значение в виде строки, не содержащей символа перевода строки.
     *
     * @param value значение
     * @return значение в виде строки
     */
    protected abstract String encodeValue(final V value);

    /**
     * Получить ключ из строкового представления ключа.
     *
     * @param s строковое представление ключа
     * @return ключ
     */
    protected abstract K decodeKey(final String s);

    /**
     * Получить значение из строкового представления значения.
     *
     * @param s строковое представление значения
     * @return значение
     */
    protected abstract V decodeValue(final String s);

    /**
     * Записать информацию о времени, ключе и значении.
     *
     * @param key   ключ
     * @param value значение
     * @param bw    писатель
     * @throws IOException если произошла ошибка ввода-вывода
     */
    public void writeEntry(final K key, final V value, final BufferedWriter bw) throws IOException {
        final long time = getTime(key, value);
        bw.write(String.valueOf(time));
        bw.newLine();
        bw.write(encodeKey(key));
        bw.newLine();
        bw.write(encodeValue(value));
        bw.newLine();
    }

    /**
     * Дописать информацию о времени, ключе и значении в конец файла.
     *
     * @param key   ключ
     * @param value значение
     * @throws IOException если произошла ошибка ввода-вывода
     */
    public void appendEntry(final K key, final V value) throws IOException {
        try (final BufferedWriter bw = Files.newBufferedWriter(Path.of(fileName),
                StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            writeEntry(key, value, bw);
        }
    }

    /**
     * Записать информацию о соответствии в файл.
     *
     * @param minTimeBound минимальное значение времени, для которого информация ещё используется
     * @throws IOException если произошла ошибка ввода-вывода
     */
    public void writeMap(final long minTimeBound) throws IOException {
        final String tmpFileName = fileName + ".tmp";
        try (final BufferedWriter bw = Files.newBufferedWriter(Path.of(tmpFileName))) {
            for (final Map.Entry<K, V> entry : map.entrySet()) {
                final K key = entry.getKey();
                final V value = entry.getValue();
                final long time = getTime(key, value);
                if (time >= minTimeBound) {
                    writeEntry(key, value, bw);
                }
            }
        }
        new File(fileName).delete();
        if (!new File(tmpFileName).renameTo(new File(fileName))) {
            throw new IOException("Cannot rename tmp-file " + tmpFileName + " to file " + fileName);
        }
    }

    /**
     * Прочитать информацию о соответствии из файла.
     *
     * @param minTimeBound минимальное значение времени, для которого информация ещё используется
     * @throws IOException если произошла ошибка ввода-вывода
     */
    public void readMap(final long minTimeBound) throws IOException {
        try (final BufferedReader br = Files.newBufferedReader(Path.of(fileName))) {
            while (true) {
                final String timeString = br.readLine();
                if (timeString == null) {
                    break;
                }
                final String keyString = br.readLine();
                if (keyString == null) {
                    break;
                }
                final String valueString = br.readLine();
                if (valueString == null) {
                    break;
                }
                try {
                    final long time = Long.parseLong(timeString);
                    if (time < minTimeBound) {
                        continue;
                    }
                } catch (final NumberFormatException e) {
                    continue;
                }
                final K key = decodeKey(keyString);
                final V value = decodeValue(valueString);
                map.put(key, value);
            }
        }
    }
}

package com.simpleutils.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Класс для работы с JSON-конфигурациями.
 */
@SuppressWarnings("unused")
public class JSONConfig {

    private final JSONObject jsonObject;

    public JSONConfig() {
        jsonObject = null;
    }

    public JSONConfig(final Object object) {
        jsonObject = (JSONObject) object;
    }

    public JSONConfig(final File file) throws IOException, ParseException, ClassCastException {
        jsonObject = (JSONObject) read(file);
    }

    public boolean getBoolean(final String key) {
        return getBoolean(jsonObject, key);
    }

    public long getLong(final String key) {
        return getLong(jsonObject, key);
    }

    public int getInt(final String key) {
        return getInt(jsonObject, key);
    }

    public double getDouble(final String key) {
        return getDouble(jsonObject, key);
    }

    public String getString(final String key) {
        return getString(jsonObject, key);
    }

    public String getStringNonNull(final String key) {
        return getStringNonNull(jsonObject, key);
    }

    public JSONObject getJSONObject(final String key) {
        return getJSONObject(jsonObject, key);
    }

    public JSONArray getJSONArray(final String key) {
        return getJSONArray(jsonObject, key);
    }

    public boolean getOrDefault(final String key, final boolean defaultValue) {
        return getOrDefault(jsonObject, key, defaultValue);
    }

    public long getOrDefault(final String key, final long defaultValue) {
        return getOrDefault(jsonObject, key, defaultValue);
    }

    public String getOrDefault(final String key, final String defaultValue) {
        return getOrDefault(jsonObject, key, defaultValue);
    }

    /**
     * Прочитать конфигурационный файл, удалить из него комментарии и преобразовать в JSONObject или JSONArray.
     *
     * @param file файл
     * @return JSONObject или JSONArray
     * @throws IOException        если произошла ошибка ввода-вывода
     * @throws ParseException     если не удалось выполнить парсинг
     * @throws ClassCastException если результат чтения из файла не является JSONObject или JSONArray
     */
    public static JSONAware read(final File file) throws IOException, ParseException, ClassCastException {
        final StringBuilder sb = new StringBuilder();
        try (final BufferedReader br = Files.newBufferedReader(file.toPath())) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.stripLeading().startsWith("//")) {
                    continue;
                }
                sb.append(line);
            }
        }
        return (JSONAware) new JSONParser().parse(sb.toString());
    }

    public static boolean getBoolean(final JSONObject json, final String key) {
        return (boolean) json.get(key);
    }

    public static long getLong(final JSONObject json, final String key) {
        return (long) json.get(key);
    }

    public static int getInt(final JSONObject json, final String key) {
        return (int) getLong(json, key);
    }

    public static double getDouble(final JSONObject json, final String key) {
        final Object o = json.get(key);
        if (o instanceof Long) {
            return (double) ((Long) o);
        } else {
            return (double) o;
        }
    }

    public static String getString(final JSONObject json, final String key) {
        return (String) json.get(key);
    }

    public static String getStringNonNull(final JSONObject json, final String key) {
        return Objects.requireNonNull((String) json.get(key));
    }

    public static JSONObject getJSONObject(final JSONObject json, final String key) {
        return (JSONObject) json.get(key);
    }

    public static JSONArray getJSONArray(final JSONObject json, final String key) {
        return (JSONArray) json.get(key);
    }

    public static boolean getOrDefault(final JSONObject json, final String key, final boolean defaultValue) {
        try {
            return (boolean) json.get(key);
        } catch (final Exception e) {
            return defaultValue;
        }
    }

    public static long getOrDefault(final JSONObject json, final String key, final long defaultValue) {
        try {
            return (long) json.get(key);
        } catch (final Exception e) {
            return defaultValue;
        }
    }

    public static String getOrDefault(final JSONObject json, final String key, final String defaultValue) {
        try {
            final String value = (String) json.get(key);
            return (value != null) ? value : defaultValue;
        } catch (final Exception e) {
            return defaultValue;
        }
    }
}

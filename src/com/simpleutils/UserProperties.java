package com.simpleutils;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * В файле ".userProperties", расположенном в папке "user.home", находится информация для конфигурирования.
 * Например, там можно размещать префиксы путей к папкам и файлам, которые могут быть различными
 * для Windows и Linux систем, либо систем различных пользователей.
 */
public class UserProperties {

    /**
     * Объект с настройками.
     */
    public static final Properties PROPERTIES;

    static {
        PROPERTIES = new Properties();
        final Path path = Path.of(System.getProperty("user.home"), ".userProperties");
        try (final Reader br = Files.newBufferedReader(path)) {
            PROPERTIES.load(br);
        } catch (final IOException e) {
            System.err.println("Cannot load user properties from " + path);
            throw new RuntimeException(e);
        }
    }

    public static String get(final String key) {
        return Objects.requireNonNull(PROPERTIES.getProperty(key));
    }

    public static String get(final String key, final String defaultValue) {
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static String userHome() {
        return Objects.requireNonNull(System.getProperty("user.home"));
    }
}

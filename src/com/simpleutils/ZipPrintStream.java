package com.simpleutils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Утилитарный класс для получения объекта типа {@link PrintStream}, который осуществляет запись в zip-файл.
 */
public class ZipPrintStream {

    private ZipPrintStream() {
        throw new UnsupportedOperationException();
    }

    /**
     * Создать объект типа {@link PrintStream}, который осуществляет запись в zip-файл.
     *
     * @param zipFile      zip-файл
     * @param zipEntryName имя zipEntry внутри zip-файла
     * @return объект типа {@link PrintStream}
     * @throws IOException если произошла ошибка ввода-вывода
     */
    public static PrintStream newZipPrintStream(final File zipFile, final String zipEntryName) throws IOException {
        final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
        zipOut.putNextEntry(new ZipEntry(zipEntryName));
        return new PrintStream(zipOut, false, StandardCharsets.UTF_8);
    }

    /**
     * Создать объект типа {@link PrintStream}, который осуществляет запись в zip-файл.
     * <p>
     * Имя zip-файла создаётся автоматически путём добавления ".zip" к имени zipEntry.
     *
     * @param zipEntryName имя zipEntry
     * @return объект типа {@link PrintStream}
     * @throws IOException если произошла ошибка ввода-вывода
     */
    public static PrintStream newZipPrintStream(final String zipEntryName) throws IOException {
        return newZipPrintStream(new File(zipEntryName + ".zip"), zipEntryName);
    }
}

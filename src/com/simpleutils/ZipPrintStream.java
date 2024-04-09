package com.simpleutils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Утилитарный класс для получения объекта типа {@link java.io.PrintStream}, который осуществляет запись в zip-файл.
 */
public class ZipPrintStream {

    private ZipPrintStream() {
        throw new UnsupportedOperationException();
    }

    public static PrintStream newZipPrintStream(final File zipFile, final String zipEntryName) throws IOException {
        final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
        zipOut.putNextEntry(new ZipEntry(zipEntryName));
        return new PrintStream(zipOut, false, StandardCharsets.UTF_8);
    }
}

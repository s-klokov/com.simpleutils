package com.simpleutils.email;

import javax.mail.*;
import javax.mail.search.SearchTerm;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class EmailReceiver {

    private final Properties properties;

    /**
     * Создать экземпляр класса для получения почты.
     *
     * @param propertiesFileName имя файла с настройками
     * @return экземпляр класса для получения почты
     * @throws IOException если произошла ошибка ввода-вывода при чтении файла настроек
     */
    public static EmailReceiver newInstance(final String propertiesFileName) throws IOException {
        final Properties properties = new Properties();
        try (final BufferedReader br = Files.newBufferedReader(Path.of(propertiesFileName))) {
            properties.load(br);
        }
        return new EmailReceiver(properties);
    }

    /**
     * Создать экземпляр класса для получения почты.
     * <p>
     * Пример файла настроек:
     * <pre>
     * protocol=pop3
     * host=pop3.domain.biz
     * port=995
     * user=user@domain.biz
     * password=12345
     * </pre>
     *
     * @param properties файл с настройками
     */
    public EmailReceiver(final Properties properties) {
        final String protocol = properties.getProperty("protocol");
        final String host = properties.getProperty("host");
        final String port = properties.getProperty("port");

        properties.put("mail.%s.host".formatted(protocol), host);
        properties.put("mail.%s.port".formatted(protocol), port);

        properties.setProperty("mail.%s.socketFactory.class".formatted(protocol), "javax.net.ssl.SSLSocketFactory");
        properties.setProperty("mail.%s.socketFactory.fallback".formatted(protocol), "false");
        properties.setProperty("mail.%s.socketFactory.port".formatted(protocol), port);

        this.properties = properties;
    }

    public final Session getSession() {
        return Session.getDefaultInstance(properties);
    }

    public final Store getStore(final Session session) throws NoSuchProviderException {
        return session.getStore(properties.getProperty("protocol"));
    }

    /**
     * Получить сообщения с сервера.
     *
     * @param store хранилище
     * @return массив сообщений
     * @throws MessagingException если произошла ошибка при работе с почтой
     */
    public Message[] getMessages(final Store store) throws MessagingException {
        store.connect(properties.getProperty("user"), properties.getProperty("password"));
        final Folder folderInbox = store.getFolder("INBOX");
        folderInbox.open(Folder.READ_ONLY);
        return folderInbox.getMessages();
    }

    /**
     * Поискать сообщения с сервера.
     *
     * @param store хранилище
     * @param term  критерий поиска
     * @return массив сообщений
     * @throws MessagingException если произошла ошибка при работе с почтой
     */
    public Message[] search(final Store store, final SearchTerm term) throws MessagingException {
        store.connect(properties.getProperty("user"), properties.getProperty("password"));
        final Folder folderInbox = store.getFolder("INBOX");
        folderInbox.open(Folder.READ_ONLY);
        return folderInbox.search(term);
    }
}

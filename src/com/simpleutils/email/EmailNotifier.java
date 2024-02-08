package com.simpleutils.email;

import com.simpleutils.logs.AbstractLogger;
import com.simpleutils.logs.SevenDaysLogger;

import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Реализация рассылки уведомлений по email.
 * <p>
 * Имеются три папки: outgoing, sent и errors. Файлы из папки outgoing отсылаются на указанные внутри файла адреса
 * и перекладываются в папку sent в случае успешной отправки и в папку errors в случае возникновения ошибок.
 * <p>
 * Файлы должны быть текстовыми и иметь вид:
 * <pre>
 * to=email1@example.com,
 * to=email2@example.com,
 * subject=Тема письма
 * attachment=путь_к_файлу (такие строки могут быть опущены)
 * Текст письма.
 * </pre>
 * Информация для аутентификации на smtp-сервере записана в файле mail.properties.
 */
class EmailNotifier {

    private static final Thread mainThread = Thread.currentThread();
    private final AbstractLogger logger;
    private EmailSender emailSender = null;
    private int minute = -1;

    private EmailNotifier() {
        logger = new SevenDaysLogger(
                "logs/" + EmailNotifier.class.getSimpleName() + ".%d.log",
                "logs/" + EmailNotifier.class.getSimpleName() + ".%d.err");
        logger.info("STARTED");
    }

    public static void main(final String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Interrupted.");
            mainThread.interrupt();
            try {
                mainThread.join(10_000);
            } catch (final InterruptedException ignored) {
            }
        }));
        new File("outgoing").mkdirs();
        new File("sent").mkdirs();
        new File("errors").mkdirs();
        final File dir = new File("logs");
        dir.mkdirs();
        System.out.println(EmailNotifier.class.getSimpleName() + " has started.");
        System.out.println("Logs are being written to " + dir.getAbsolutePath());
        System.out.println("Press Ctrl+C to stop the program.");
        final EmailNotifier emailNotifier = new EmailNotifier();
        try {
            emailNotifier.init();
            emailNotifier.run();
        } catch (final RuntimeException | IOException e) {
            emailNotifier.logger.log(AbstractLogger.ERROR, e.getMessage(), e);
        } finally {
            emailNotifier.done();
        }
    }

    private void init() throws IOException {
        emailSender = EmailSender.newInstance("mail.properties");
    }

    private void run() {
        while (!Thread.interrupted()) {
            processOutgoingFiles();
            printRunningHourly();
            try {
                //noinspection BusyWait
                Thread.sleep(1000L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void printRunningHourly() {
        final long now = System.currentTimeMillis();
        final int min = (int) ((now / 1000L / 60L) % 60L);
        if (minute != min) {
            minute = min;
            if (minute == 0) {
                logger.info("Running.");
            }
        }
    }

    private void processOutgoingFiles() {
        final File[] files = new File("outgoing").listFiles();
        if (files == null) return;
        for (final File file : files) {
            if (file.isFile()) {
                try {
                    final Path path = file.toPath();
                    if (System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis() < 5000) {
                        continue;
                    }
                    final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    final List<String> toList = new ArrayList<>();
                    final List<String> attachmentList = new ArrayList<>();
                    String subject = "No subject";
                    final StringBuilder message = new StringBuilder();
                    for (final String line : lines) {
                        if (message.isEmpty()) {
                            if (line.startsWith("to=") && line.contains("@")) {
                                toList.add(line.substring("to=".length()).trim());
                            } else if (line.startsWith("subject=")) {
                                subject = line.substring("subject=".length()).trim();
                            } else if (line.startsWith("attachment=")) {
                                attachmentList.add(line.substring("attachment=".length()).trim());
                            } else {
                                message.append(line).append("\r\n");
                            }
                        } else {
                            message.append(line).append("\r\n");
                        }
                    }
                    for (final String to : toList) {
                        logger.info("Sending mail to " + to + "...");
                        sendEmail(emailSender.login, to, subject, message.toString(), attachmentList);
                        try {
                            Thread.sleep(1000L);
                        } catch (final InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    logger.info("Moving file " + file.getName() + " to sent folder...");
                    Files.move(path, new File("sent", file.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    logger.info("Done.");
                } catch (final IOException e) {
                    logger.log(AbstractLogger.ERROR, "Cannot process file " + file.getAbsolutePath(), e);
                    logger.info("Moving file " + file.getName() + " to errors folder...");
                    file.renameTo(new File("errors", file.getName()));
                    if (file.exists()) {
                        file.delete();
                    }
                    logger.info("Done.");
                }
            }
        }
    }

    private void sendEmail(final String from,
                           final String to,
                           final String subject,
                           final String text,
                           final List<String> attachmentList) {
        try {
            emailSender.sendEmail(from, to, subject, text, attachmentList);
            try {
                Thread.sleep(1000L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (final MessagingException mex) {
            logger.log(AbstractLogger.ERROR, "Cannot send email to " + to, mex);
        }
    }

    private void done() {
        logger.info("FINISHED.\r\n");
        logger.close();
    }
}

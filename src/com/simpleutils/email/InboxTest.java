package com.simpleutils.email;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import java.io.IOException;

public class InboxTest {

    public static void main(final String[] args) {
        final EmailReceiver emailReceiver;
        try {
            emailReceiver = EmailReceiver.newInstance("emailReceiver.properties");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        final Session session = emailReceiver.getSession();
        try (final Store store = emailReceiver.getStore(session)) {
            final Message[] messages = emailReceiver.getMessages(store);
            for (final Message message : messages) {
                System.out.println(message.getSubject());
            }
        } catch (final MessagingException e) {
            e.printStackTrace(System.err);
        }
    }
}

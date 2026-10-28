package com.amiva;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

// JavaMailSender de tests: guarda los mensajes en memoria en lugar de hablar con un SMTP.
// failing = true simula un SMTP caído
public class CapturingMailSender extends JavaMailSenderImpl {

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        if (failing) {
            throw new MailSendException("SMTP simulado caído");
        }
        // Como hace el envío real: fija las cabeceras (Content-Type del multipart incluido)
        for (MimeMessage message : mimeMessages) {
            try {
                message.saveChanges();
            } catch (MessagingException e) {
                throw new MailSendException("Mensaje no válido", e);
            }
            sent.add(message);
        }
    }

    public void reset() {
        sent.clear();
        failing = false;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    // Mensajes enviados a un destinatario (los tests comparten contexto y pueden registrar a otros)
    public List<MimeMessage> sentTo(String email) {
        return sent.stream().filter(m -> hasRecipient(m, email)).toList();
    }

    // Parte text/plain ya decodificada
    public static String plainText(Part part) {
        try {
            if (part.isMimeType("text/plain")) {
                return (String) part.getContent();
            }
            if (part.getContent() instanceof MimeMultipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    String text = plainText(multipart.getBodyPart(i));
                    if (text != null) {
                        return text;
                    }
                }
            }
            return null;
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasRecipient(MimeMessage message, String email) {
        try {
            Address[] recipients = message.getAllRecipients();
            return recipients != null && List.of(recipients).stream()
                    .anyMatch(a -> a.toString().equalsIgnoreCase(email));
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }
}

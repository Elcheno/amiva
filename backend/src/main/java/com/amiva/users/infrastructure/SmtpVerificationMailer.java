package com.amiva.users.infrastructure;

import com.amiva.users.application.VerificationMailer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

// Email de verificación en texto plano + HTML sencillo. El enlace lleva al front, que hace el POST a la API
@Component
public class SmtpVerificationMailer implements VerificationMailer {

    static final String SUBJECT = "Confirma tu email en Amiva";

    private final JavaMailSender mailSender;
    private final String from;
    private final String appBaseUrl;

    public SmtpVerificationMailer(JavaMailSender mailSender, @Value("${amiva.mail.from}") String from,
            @Value("${amiva.app.base-url}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public void send(String to, String displayName, String token) {
        // El token es base64url: no necesita codificarse en la URL
        String link = appBaseUrl + "/verify-email?token=" + token;
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(plainText(displayName, link), html(displayName, link));
        } catch (MessagingException e) {
            throw new MailPreparationException("No se pudo preparar el email de verificación", e);
        }
        mailSender.send(message);
    }

    private static String plainText(String displayName, String link) {
        return """
                Hola, %s:

                Confirma tu email en Amiva abriendo este enlace:

                %s

                El enlace caduca en 24 horas. Si no has creado una cuenta en Amiva, ignora este mensaje.
                """.formatted(displayName, link);
    }

    private static String html(String displayName, String link) {
        String name = HtmlUtils.htmlEscape(displayName);
        String href = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html>
                <html lang="es">
                <body style="font-family: sans-serif; line-height: 1.5;">
                  <p>Hola, %s:</p>
                  <p>Confirma tu email en Amiva:</p>
                  <p><a href="%s">Confirmar mi email</a></p>
                  <p>Si el botón no funciona, copia este enlace en el navegador:<br>%s</p>
                  <p>El enlace caduca en 24 horas. Si no has creado una cuenta en Amiva, ignora este mensaje.</p>
                </body>
                </html>
                """.formatted(name, href, href);
    }
}

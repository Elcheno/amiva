package com.amiva.users.infrastructure;

import com.amiva.users.application.InvitationMailer;
import com.amiva.users.domain.OrgRole;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

// Email de invitación en texto plano + HTML sencillo. El enlace lleva al front, que muestra la invitación
@Component
public class SmtpInvitationMailer implements InvitationMailer {

    private final JavaMailSender mailSender;
    private final String from;
    private final String appBaseUrl;

    public SmtpInvitationMailer(JavaMailSender mailSender, @Value("${amiva.mail.from}") String from,
            @Value("${amiva.app.base-url}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public void send(String to, String inviteeName, String organizationName, String inviterName, OrgRole role,
            String token) {
        // El token es base64url: no necesita codificarse en la URL
        String link = appBaseUrl + "/invite/" + token;
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject(organizationName));
            helper.setText(plainText(inviteeName, organizationName, inviterName, role, link),
                    html(inviteeName, organizationName, inviterName, role, link));
        } catch (MessagingException e) {
            throw new MailPreparationException("No se pudo preparar el email de invitación", e);
        }
        mailSender.send(message);
    }

    static String subject(String organizationName) {
        return "Te han invitado a " + organizationName + " en Amiva";
    }

    private static String roleName(OrgRole role) {
        return switch (role) {
            case OWNER -> "propietario";
            case ADMIN -> "administrador";
            case MEMBER -> "miembro";
        };
    }

    private static String plainText(String inviteeName, String organizationName, String inviterName, OrgRole role,
            String link) {
        return """
                Hola, %s:

                %s te ha invitado a unirte a %s en Amiva como %s.

                Abre este enlace para ver la invitación:

                %s

                Si no esperabas esta invitación, ignora este mensaje.
                """.formatted(inviteeName, inviterName, organizationName, roleName(role), link);
    }

    private static String html(String inviteeName, String organizationName, String inviterName, OrgRole role,
            String link) {
        String name = HtmlUtils.htmlEscape(inviteeName);
        String inviter = HtmlUtils.htmlEscape(inviterName);
        String org = HtmlUtils.htmlEscape(organizationName);
        String href = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html>
                <html lang="es">
                <body style="font-family: sans-serif; line-height: 1.5;">
                  <p>Hola, %s:</p>
                  <p>%s te ha invitado a unirte a <strong>%s</strong> en Amiva como %s.</p>
                  <p><a href="%s">Ver la invitación</a></p>
                  <p>Si el botón no funciona, copia este enlace en el navegador:<br>%s</p>
                  <p>Si no esperabas esta invitación, ignora este mensaje.</p>
                </body>
                </html>
                """.formatted(name, inviter, org, roleName(role), href, href);
    }
}

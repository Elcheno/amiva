package com.amiva.users.application;

import com.amiva.users.domain.OrgRole;

// Envía el email con el enlace de la invitación (implementación SMTP en infrastructure)
public interface InvitationMailer {

    void send(String to, String inviteeName, String organizationName, String inviterName, OrgRole role, String token);
}

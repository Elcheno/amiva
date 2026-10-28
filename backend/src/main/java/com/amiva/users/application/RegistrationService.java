package com.amiva.users.application;

import java.time.ZoneId;

import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.Organization;
import com.amiva.users.domain.OrganizationCreated;
import com.amiva.users.domain.UserRegistered;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    static final String DEFAULT_TIMEZONE = "UTC";
    private static final String EMAIL_UNIQUE_INDEX = "uq_app_user_email_lower";

    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final ApplicationEventPublisher events;

    public RegistrationService(AppUserRepository users, OrganizationRepository organizations,
            MembershipRepository memberships, PasswordEncoder passwordEncoder, UsernameGenerator usernameGenerator,
            ApplicationEventPublisher events) {
        this.users = users;
        this.organizations = organizations;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.usernameGenerator = usernameGenerator;
        this.events = events;
    }

    // Crea el usuario, su organización propia y su membership OWNER en una sola transacción
    @Transactional
    public RegistrationResult register(RegisterCommand command) {
        String email = command.email().strip();
        String displayName = command.displayName().strip();

        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        // Username provisional aleatorio; el usuario podrá cambiarlo
        String username = usernameGenerator.generateUnique();
        AppUser user = AppUser.withPassword(email, passwordEncoder.encode(command.password()), username, displayName);
        try {
            // flush para que una carrera con el mismo email salte aquí y no al hacer commit
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (isEmailUniqueViolation(e)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw e;
        }

        Organization org = organizations.save(Organization.ownedBy(user, resolveTimezone(command.timezone())));
        memberships.save(Membership.owner(org));
        events.publishEvent(new OrganizationCreated(org.getId(), org.getName(), user.asActor()));
        // El email de verificación sale tras el commit (VerificationEmailListener)
        events.publishEvent(new UserRegistered(user.getId(), user.getEmail(), user.getDisplayName()));

        return new RegistrationResult(user.getId(), user.getEmail(), user.getUsername(), user.getDisplayName(),
                org.getId(), org.getName());
    }

    // Zona IANA conocida; si falta o no es válida, UTC
    static String resolveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        String candidate = timezone.strip();
        return ZoneId.getAvailableZoneIds().contains(candidate) ? candidate : DEFAULT_TIMEZONE;
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cve
                && EMAIL_UNIQUE_INDEX.equalsIgnoreCase(cve.getConstraintName());
    }
}

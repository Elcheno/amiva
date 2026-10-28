package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.UUID;

import com.amiva.shared.domain.Actor;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.Organization;
import com.amiva.users.domain.OrganizationCreated;
import com.amiva.users.domain.UserRegistered;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    AppUserRepository users;

    @Mock
    OrganizationRepository organizations;

    @Mock
    MembershipRepository memberships;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    UsernameGenerator usernameGenerator;

    @Mock
    ApplicationEventPublisher events;

    RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, organizations, memberships, passwordEncoder, usernameGenerator, events);
    }

    @Test
    void creaUsuarioYOrganizacionPropia() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(passwordEncoder.encode("secreta123")).thenReturn("hash");
        when(usernameGenerator.generateUnique()).thenReturn("nutria-dormilona-42");
        when(users.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0), userId));
        when(organizations.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), orgId));

        RegistrationResult result = service.register(
                new RegisterCommand("ana@example.com", "secreta123", "Ana", "Europe/Madrid"));

        ArgumentCaptor<AppUser> user = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(user.capture());
        assertThat(user.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(user.getValue().getUsername()).isEqualTo("nutria-dormilona-42");
        assertThat(user.getValue().isEmailVerified()).isFalse();
        assertThat(user.getValue().getPlatformRole()).isEqualTo("user");

        ArgumentCaptor<Organization> org = ArgumentCaptor.forClass(Organization.class);
        verify(organizations).save(org.capture());
        assertThat(org.getValue().getOwnerId()).isEqualTo(userId);
        assertThat(org.getValue().getTimezone()).isEqualTo("Europe/Madrid");
        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        verify(memberships).save(membership.capture());
        assertThat(membership.getValue().getOrganizationId()).isEqualTo(orgId);
        assertThat(membership.getValue().getUserId()).isEqualTo(userId);
        assertThat(membership.getValue().getRole()).isEqualTo(OrgRole.OWNER);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(published.capture());
        assertThat(published.getAllValues()).containsExactly(
                new OrganizationCreated(orgId, "Org de Ana", new Actor(userId, "Ana", false)),
                new UserRegistered(userId, "ana@example.com", "Ana"));

        assertThat(result).isEqualTo(new RegistrationResult(userId, "ana@example.com", "nutria-dormilona-42", "Ana",
                orgId, "Org de Ana"));
    }

    @Test
    void normalizaEmailYNombreYUsaUtcSiLaZonaNoEsValida() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(users.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0), UUID.randomUUID()));
        when(organizations.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), UUID.randomUUID()));

        RegistrationResult result = service.register(
                new RegisterCommand("  Ana@Example.com ", "secreta123", "  Ana  ", "Marte/Olympus"));

        verify(users).existsByEmailIgnoreCase("Ana@Example.com");
        assertThat(result.email()).isEqualTo("Ana@Example.com");
        assertThat(result.displayName()).isEqualTo("Ana");
        ArgumentCaptor<Organization> org = ArgumentCaptor.forClass(Organization.class);
        verify(organizations).save(org.capture());
        assertThat(org.getValue().getTimezone()).isEqualTo("UTC");
    }

    @Test
    void zonaHorariaPorDefecto() {
        assertThat(RegistrationService.resolveTimezone(null)).isEqualTo("UTC");
        assertThat(RegistrationService.resolveTimezone(" ")).isEqualTo("UTC");
        assertThat(RegistrationService.resolveTimezone("America/Bogota")).isEqualTo("America/Bogota");
    }

    @Test
    void nombreDeOrganizacionNoSuperaElMaximo() {
        AppUser owner = withId(AppUser.withPassword("a@b.c", "hash", "abc", "x".repeat(150)), UUID.randomUUID());

        assertThat(Organization.ownedBy(owner, "UTC").getName()).hasSize(Organization.NAME_MAX_LENGTH);
    }

    @Test
    void rechazaEmailDuplicadoSinGuardar() {
        when(users.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ana@example.com", "secreta123", "Ana", null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(usernameGenerator, never()).generateUnique();
        verify(users, never()).saveAndFlush(any());
        verify(organizations, never()).save(any());
        verify(memberships, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void traduceLaViolacionDelIndiceUnicoEnCarrera() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicado",
                new ConstraintViolationException("duplicado", new SQLException(), "uq_app_user_email_lower")));

        assertThatThrownBy(() -> service.register(
                new RegisterCommand("ana@example.com", "secreta123", "Ana", null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(organizations, never()).save(any());
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}

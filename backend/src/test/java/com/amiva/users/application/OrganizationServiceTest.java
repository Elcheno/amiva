package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.MembershipRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    MembershipRepository memberships;

    @Mock
    OrganizationRepository organizations;

    @Mock
    OrgPermissionService permissions;

    OrganizationService service;

    private final AppUser ana = user("Ana");

    @BeforeEach
    void setUp() {
        service = new OrganizationService(memberships, organizations, permissions);
    }

    @Test
    void laPropiaPrimeroYLuegoPorNombreSinDistinguirMayusculas() {
        Organization own = withId(Organization.ownedBy(ana, "UTC"), UUID.randomUUID());
        Organization zeta = orgOf("Zeta");
        Organization beta = orgOf("beta");
        Organization alfa = orgOf("Alfa");
        List<Membership> mine = List.of(
                Membership.member(zeta, ana, OrgRole.MEMBER),
                Membership.member(beta, ana, OrgRole.ADMIN),
                Membership.owner(own),
                Membership.member(alfa, ana, OrgRole.MEMBER));
        when(memberships.findByUserId(ana.getId())).thenReturn(mine);
        when(organizations.findAllById(mine.stream().map(Membership::getOrganizationId).toList()))
                .thenReturn(List.of(zeta, beta, own, alfa));

        List<MyOrganization> result = service.listMine(ana.getId());

        assertThat(result).extracting(MyOrganization::name)
                .containsExactly("Org de Ana", "Alfa", "beta", "Zeta");
        assertThat(result).extracting(MyOrganization::own).containsExactly(true, false, false, false);
        assertThat(result).extracting(MyOrganization::role)
                .containsExactly(OrgRole.OWNER, OrgRole.MEMBER, OrgRole.ADMIN, OrgRole.MEMBER);
    }

    @Test
    void mismoNombreSeDesempataPorId() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        List<MyOrganization> sorted = List.of(
                        new MyOrganization(second, "Club", OrgRole.MEMBER, false),
                        new MyOrganization(first, "club", OrgRole.MEMBER, false))
                .stream()
                .sorted(OrganizationService.MY_ORGANIZATION_ORDER)
                .toList();

        assertThat(sorted).extracting(MyOrganization::orgId).containsExactly(first, second);
    }

    private Organization orgOf(String name) {
        Organization org = withId(Organization.ownedBy(user("x"), "UTC"), UUID.randomUUID());
        ReflectionTestUtils.setField(org, "name", name);
        return org;
    }

    private static AppUser user(String name) {
        return withId(AppUser.withPassword(name + "@example.com", "hash", name), UUID.randomUUID());
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}

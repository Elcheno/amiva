package com.amiva.users.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amiva.users.application.GroupSummary;
import com.amiva.users.domain.MemberGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberGroupRepository extends JpaRepository<MemberGroup, UUID> {

    // Un grupo de otra organización es como si no existiera
    Optional<MemberGroup> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // Grupos de la organización con su número de miembros, por nombre sin distinguir mayúsculas
    @Query("""
            select new com.amiva.users.application.GroupSummary(g.id, g.name, count(m.userId))
            from MemberGroup g left join MemberGroupMember m on m.groupId = g.id
            where g.organizationId = :orgId
            group by g.id, g.name
            order by lower(g.name), g.id
            """)
    List<GroupSummary> findSummaries(@Param("orgId") UUID organizationId);

    @Query("""
            select count(g) > 0 from MemberGroup g
            where g.organizationId = :orgId and lower(g.name) = lower(:name)
            """)
    boolean existsByName(@Param("orgId") UUID organizationId, @Param("name") String name);

    @Query("""
            select count(g) > 0 from MemberGroup g
            where g.organizationId = :orgId and lower(g.name) = lower(:name) and g.id <> :excludedId
            """)
    boolean existsByNameExcluding(@Param("orgId") UUID organizationId, @Param("name") String name,
            @Param("excludedId") UUID excludedId);
}

package com.amiva.users.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amiva.users.domain.AppUser;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    // lower() para aprovechar el índice único uq_app_user_email_lower
    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCase(@Param("email") String email);

    // lower() para aprovechar el índice único uq_app_user_username_lower
    @Query("select count(u) > 0 from AppUser u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    // Usuarios activos que aún no son miembros de la organización, por inicio del username o parte del nombre.
    // prefix y contains llegan en minúsculas con % _ \ ya escapados. Sin índice: búsqueda simple por ahora
    @Query("""
            select u from AppUser u
            where u.enabled = true
              and (lower(u.username) like :prefix escape '\\' or lower(u.displayName) like :contains escape '\\')
              and not exists (select 1 from Membership m where m.organizationId = :orgId and m.userId = u.id)
            order by lower(u.displayName), u.username
            """)
    List<AppUser> searchInvitable(@Param("orgId") UUID organizationId, @Param("prefix") String prefix,
            @Param("contains") String contains, Limit limit);
}

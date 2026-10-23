package com.amiva.users.infrastructure;

import java.util.UUID;

import com.amiva.users.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    // lower() para aprovechar el índice único uq_app_user_email_lower
    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}

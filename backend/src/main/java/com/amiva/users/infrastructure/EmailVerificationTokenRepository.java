package com.amiva.users.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.amiva.users.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    // Último emitido al usuario (usado o no): limita los reenvíos
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    // Los enlaces anteriores dejan de valer al emitir uno nuevo
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailVerificationToken t where t.userId = :userId and t.usedAt is null")
    void deleteUnusedByUserId(@Param("userId") UUID userId);
}

package com.aifsar.pos.issuer.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssuerAuthorizationRepository extends JpaRepository<IssuerAuthorization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from IssuerAuthorization a "
            + "where a.terminalId = :terminalId and a.stan = :stan and a.rrn = :rrn")
    Optional<IssuerAuthorization> findForUpdate(@Param("terminalId") String terminalId,
                                                @Param("stan") String stan, @Param("rrn") String rrn);
}

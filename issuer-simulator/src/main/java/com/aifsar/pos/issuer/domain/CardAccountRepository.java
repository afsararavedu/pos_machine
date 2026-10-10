package com.aifsar.pos.issuer.domain;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardAccountRepository extends JpaRepository<CardAccount, String> {

    /** Serialises every balance change on one card (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CardAccount c where c.cardNumber = :card")
    Optional<CardAccount> lock(@Param("card") String cardNumber);
}

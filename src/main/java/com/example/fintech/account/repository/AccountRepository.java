package com.example.fintech.account.repository;

import com.example.fintech.account.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from Account a
            where a.id = :id
            """)
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from Account a
        where a.user.id = :userId
          and a.currency = :currency
        """)
    Optional<Account> findByUserIdAndCurrencyForUpdate(
            @Param("userId") UUID userId,
            @Param("currency") String currency
    );
}
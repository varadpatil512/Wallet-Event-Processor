package org.example.Dao;

import jakarta.persistence.LockModeType;
import org.example.Model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepo extends JpaRepository<Wallet,Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wallet FROM Wallet wallet WHERE wallet.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    Optional<Wallet> findByUserId(UUID userId);
}

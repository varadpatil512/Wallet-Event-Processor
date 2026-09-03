package org.example.Dao;

import org.example.Model.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRecordRepo extends JpaRepository<TransactionRecord,Integer> {

    Optional<TransactionRecord> findByTransactionID(UUID transactionID);
}

package org.example.Model;

import jakarta.persistence.*;
import lombok.Data;
import org.example.Enum.TransactionStatus;
import org.example.Enum.TransactionType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Data
public class TransactionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(unique = true)
    private UUID transactionID;
    private UUID userId;
    private BigDecimal amount;
    private String response;
    private TransactionStatus status;
    private TransactionType type;
    private Timestamp createdAt;
}

package org.example.Model;

import jakarta.persistence.Id;
import lombok.Data;
import org.example.Enum.TransactionStatus;
import org.example.Enum.TransactionType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Data
public class TransactionRecord {
    @Id
    private int id;
    private UUID transID;
    private UUID userId;
    private BigDecimal amount;
    private String response;
    private TransactionStatus status;
    private TransactionType type;
    private Timestamp createdAt;
}

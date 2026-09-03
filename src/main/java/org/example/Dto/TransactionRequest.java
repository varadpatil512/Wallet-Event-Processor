package org.example.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.example.Enum.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransactionRequest {
    @NotNull
    private UUID transactionId;

    @NotNull
    private UUID userId;

    @Positive
    private BigDecimal amount;

    @NotNull
    private TransactionType type;

}

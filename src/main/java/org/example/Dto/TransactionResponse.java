package org.example.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.example.Enum.TransactionStatus;
import org.example.Enum.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransactionResponse {
    @NotNull
    private UUID transactionID;

    private TransactionStatus status;

    private BigDecimal newBalance;

    private String response;

}

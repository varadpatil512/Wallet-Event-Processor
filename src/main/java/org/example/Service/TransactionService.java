package org.example.Service;

import lombok.RequiredArgsConstructor;
import org.example.Dao.TransactionRecordRepo;
import org.example.Dao.WalletRepo;
import org.example.Dto.TransactionRequest;
import org.example.Dto.TransactionResponse;
import org.example.Enum.TransactionStatus;
import org.example.Exceptions.InsufficientFundsException;
import org.example.Exceptions.WalletNotFoundException;
import org.example.Model.TransactionRecord;
import org.example.Model.Wallet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletRepo walletRepository;
    private final TransactionRecordRepo transactionRecordRepository;
    private final TransactionClaimService transactionClaimService;

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {

        boolean claimed;
        try {
            transactionClaimService.claim(request);
            claimed = true;
        } catch (DataIntegrityViolationException ex) {
            claimed = false;
        }


        if (!claimed) {
            TransactionRecord existing = transactionRecordRepository
                    .findByTransactionID(request.getTransactionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Duplicate detected but original record not found — should not happen"));
            return buildResponseFromRecord(existing);
        }

        TransactionRecord record = transactionRecordRepository
                .findByTransactionID(request.getTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed record not found immediately after claiming — should not happen"));



        Wallet wallet = walletRepository.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for userId: " + request.getUserId()));


        BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            record.setStatus(TransactionStatus.FAILED);
            record.setResponse("Insufficient funds");
            transactionRecordRepository.save(record);
            throw new InsufficientFundsException(
                    "Insufficient funds for userId: " + request.getUserId());
        }


        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        record.setStatus(TransactionStatus.SUCCESS);
        record.setResponse("Transaction processed successfully");
        transactionRecordRepository.save(record);



        return TransactionResponse.builder()
                .transactionID(record.getTransactionID())
                .status(TransactionStatus.SUCCESS)
                .newBalance(newBalance)
                .response(record.getResponse())
                .build();
    }




    private TransactionResponse buildResponseFromRecord(TransactionRecord record) {
        return TransactionResponse.builder()
                .transactionID(record.getTransactionID())
                .status(record.getStatus())
                .newBalance(null) // optionally look up current wallet balance if you want this populated
                .response(record.getResponse())
                .build();
    }







}
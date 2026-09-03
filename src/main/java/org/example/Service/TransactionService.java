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

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {

        TransactionRecord record = new TransactionRecord();
        record.setTransactionID(request.getTransactionId());
        record.setUserId(request.getUserId());
        record.setAmount(request.getAmount());
        record.setType(request.getType());
        record.setStatus(TransactionStatus.PROCESSING);

        try {
            transactionRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {

            TransactionRecord existing = transactionRecordRepository
                    .findByTransactionID(request.getTransactionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Duplicate detected but original record not found — should not happen"));
            return buildResponseFromRecord(existing);
        }



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
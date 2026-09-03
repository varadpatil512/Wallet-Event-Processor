package org.example.Service;

import lombok.RequiredArgsConstructor;
import org.example.Dao.TransactionRecordRepo;
import org.example.Dao.WalletRepo;
import org.example.Dto.TransactionRequest;
import org.example.Dto.TransactionResponse;
import org.example.Enum.TransactionStatus;
import org.example.Model.TransactionRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    }
}
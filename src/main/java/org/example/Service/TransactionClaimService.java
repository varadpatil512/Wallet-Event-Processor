package org.example.Service;

import lombok.RequiredArgsConstructor;
import org.example.Dao.TransactionRecordRepo;
import org.example.Dto.TransactionRequest;
import org.example.Enum.TransactionStatus;
import org.example.Model.TransactionRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionClaimService {

    private final TransactionRecordRepo transactionRecordRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(TransactionRequest request) {
        TransactionRecord record = new TransactionRecord();
        record.setTransactionID(request.getTransactionId());
        record.setUserId(request.getUserId());
        record.setAmount(request.getAmount());
        record.setType(request.getType());
        record.setStatus(TransactionStatus.PROCESSING);

        transactionRecordRepository.saveAndFlush(record);
    }
}

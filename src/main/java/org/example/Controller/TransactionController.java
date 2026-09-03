package org.example.Controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.Dto.TransactionRequest;
import org.example.Dto.TransactionResponse;
import org.example.Service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/process")
    public ResponseEntity<TransactionResponse> processTransaction(@Valid @RequestBody TransactionRequest request) {

        return new ResponseEntity<>(transactionService.processTransaction(request), HttpStatus.OK);

    }


}
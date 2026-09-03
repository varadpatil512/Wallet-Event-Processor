package org.example;

import org.example.Dao.WalletRepo;
import org.example.Dto.TransactionRequest;
import org.example.Dto.TransactionResponse;
import org.example.Enum.TransactionType;
import org.example.Model.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TransactionServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepo walletRepository;

    private UUID userId;

    private static final String ENDPOINT = "/api/v1/transactions/process";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("500.00"));
        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("Processes a single valid debit transaction successfully.")
    void happyPath_singleDebit_succeeds() {
        TransactionRequest request = buildRequest(UUID.randomUUID(), new BigDecimal("250.00"));

        ResponseEntity<TransactionResponse> response =
                restTemplate.postForEntity(ENDPOINT, request, TransactionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("250.00"), response.getBody().getNewBalance());

        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        assertEquals(new BigDecimal("250.00"), updated.getBalance());

        System.out.println("[Happy Path] Debited 250.00 successfully. New balance: " + updated.getBalance());
    }

    @Test
    @DisplayName("Sends 3 identical transactionIDs simultaneously. Ensures the balance is only deducted once.")
    void idempotency_threeIdenticalRequests_onlyDeductsOnce() throws InterruptedException {
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        int threadCount = 3;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<ResponseEntity<TransactionResponse>> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait for the starting gun
                    TransactionRequest request = buildRequestWithId(transactionId, amount);
                    ResponseEntity<TransactionResponse> response =
                            restTemplate.postForEntity(ENDPOINT, request, TransactionResponse.class);
                    responses.add(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all 3 threads at once
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, responses.size(), "All 3 requests should have completed");

        long successCount = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.OK)
                .count();
        assertEquals(3, successCount, "All 3 should return 200 (one real success + two replays)");

        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        assertEquals(new BigDecimal("400.00"), updated.getBalance(),
                "Balance should only be debited once (500 - 100 = 400), not three times");

        System.out.println("[Idempotency] 3 identical requests sent. Final balance: " + updated.getBalance()
                + " (expected 400.00 — deducted exactly once)");
    }

    @Test
    @DisplayName("Sends 10 concurrent debit requests of ₹100 for a wallet with a ₹500 balance. Ensures the final balance is exactly ₹0 and 5 requests fail with insufficient funds.")
    void raceCondition_tenConcurrentDebits_exactlyFiveSucceed() throws InterruptedException {
        BigDecimal amount = new BigDecimal("100.00");
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<ResponseEntity<TransactionResponse>> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionRequest request = buildRequest(UUID.randomUUID(), amount);
                    ResponseEntity<TransactionResponse> response =
                            restTemplate.postForEntity(ENDPOINT, request, TransactionResponse.class);
                    responses.add(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, responses.size(), "All 10 requests should have completed");

        long successCount = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.OK)
                .count();
        long failureCount = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .count();

        assertEquals(5, successCount, "Exactly 5 debits should succeed");
        assertEquals(5, failureCount, "Exactly 5 debits should fail with insufficient funds");

        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        assertEquals(BigDecimal.ZERO.setScale(2), updated.getBalance().setScale(2),
                "Final balance must be exactly 0, no negative balance, no overshoot");

        System.out.println("[Race Condition] 10 concurrent debits sent. Success: " + successCount
                + ", Failed: " + failureCount + ", Final balance: " + updated.getBalance());
    }



    private TransactionRequest buildRequest(UUID transactionId, BigDecimal amount) {
        return buildRequestWithId(transactionId, amount);
    }

    private TransactionRequest buildRequestWithId(UUID transactionId, BigDecimal amount) {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionId(transactionId);
        request.setUserId(userId);
        request.setAmount(amount);
        request.setType(TransactionType.DEBIT);
        return request;
    }
}

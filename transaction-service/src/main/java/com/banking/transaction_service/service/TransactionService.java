package com.banking.transaction_service.service;

import com.banking.transaction_service.client.AccountServiceClient;
import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.entity.TransactionType;
import com.banking.transaction_service.event.TransactionInitiatedEvent;
import com.banking.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    public TransactionResponse transfer(TransferRequest request) {
        log.info("SAGA START - Transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());
        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());
        Transaction transaction = Transaction.builder()
                .senderAccountNumber(request.getSenderAccountNumber())
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSING)
                .description(request.getDescription())
                .referenceNumber(UUID.randomUUID().toString())
                .build();

        Transaction savedTransactions = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", savedTransactions.getId());

        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransactions.getId(),
                savedTransactions.getSenderAccountNumber(),
                savedTransactions.getReceiverAccountNumber(),
                savedTransactions.getAmount(),
                savedTransactions.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, savedTransactions.getId(), event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", savedTransactions.getId());
        return mapToResponse(savedTransactions);
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(
                transactionRepository.findById(transactionId)
                        .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId))
        );
    }

    public List<TransactionResponse> getTransactionMistory(String accountNumber) {
        return transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .senderAccountNumber(transaction.getSenderAccountNumber())
                .receiverAccountNumber(transaction.getReceiverAccountNumber())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .referenceNumber(transaction.getReferenceNumber())
                .failureReason(transaction.getFailureReason())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }
}

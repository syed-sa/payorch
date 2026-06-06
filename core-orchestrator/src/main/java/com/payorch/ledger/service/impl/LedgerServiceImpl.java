package com.payorch.ledger.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payorch.ledger.exception.AccountNotFoundException;
import com.payorch.ledger.exception.InsufficientFundsException;
import com.payorch.ledger.repository.AccountRepository;
import com.payorch.ledger.repository.LedgerRepository;
import com.payorch.ledger.service.LedgerService;
import com.payorch.model.Account;
import com.payorch.model.LedgerEntry;
import com.payorch.model.Transaction;
import com.payorch.outbox.model.OutboxEvent;
import com.payorch.outbox.repository.OutboxRepository;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final OutboxRepository outboxRepository; // Injected Outbox

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordEntry(Transaction transaction) {
        log.info("Starting atomic ledger entry for transaction: {}", transaction.getId());

        // 1. Fetch accounts with Pessimistic Lock
        // This blocks other threads from modifying these specific rows until this @Transactional method finishes.
        Account sender = accountRepository.findByIdWithLock(transaction.getSenderAccount().getId())
                .orElseThrow(() -> new AccountNotFoundException("Sender not found"));

        Account receiver = accountRepository.findByIdWithLock(transaction.getReceiverAccount().getId())
                .orElseThrow(() -> new AccountNotFoundException("Receiver not found"));

        BigDecimal amount = transaction.getAmount();

        // 2. Validate Funds
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance for sender: " + sender.getId());
        }

        // 3. Update Balances
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        accountRepository.save(sender);
        accountRepository.save(receiver);

        // 4. Record Double-Entry Ledger rows
        saveLedgerRow(transaction, sender, amount.negate(), "DEBIT");
        saveLedgerRow(transaction, receiver, amount, "CREDIT");

        // 5. Transactional Outbox (The Completion of Phase 1)
        // We save the event to the DB. If the method fails here, balances & ledger are rolled back.
        // If it succeeds, the Outbox Worker (Phase 4) will eventually pick this up.
        saveOutboxEvent(transaction);

        log.info("Ledger balanced and Outbox event created successfully for txn: {}", transaction.getId());
    }

    private void saveLedgerRow(Transaction txn, Account acc, BigDecimal amt, String type) {
        LedgerEntry entry = LedgerEntry.builder()
                .transaction(txn)
                .account(acc)
                .amount(amt)
                .entryType(type)
                .build();
        ledgerRepository.save(entry);
    }

    private void saveOutboxEvent(Transaction transaction) {
        // We store the minimal JSON payload needed for Kafka/Webhooks later
        String payload = String.format(
            "{\"transactionId\":\"%s\", \"status\":\"%s\", \"amount\":%s, \"provider\":\"%s\"}",
            transaction.getId(),
            transaction.getStatus(),
            transaction.getAmount(),
            transaction.getProviderId()
        );

        OutboxEvent event = OutboxEvent.builder()
                .eventType("PAYMENT_SUCCESS_INTERNAL")
                .payload(payload)
                .status("PENDING")
                .build();

        outboxRepository.save(event);
    }
}
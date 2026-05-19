package com.payorch.ledger.service;

import com.payorch.ledger.model.Transaction;

public interface LedgerService {
    void recordEntry(Transaction transaction);
}
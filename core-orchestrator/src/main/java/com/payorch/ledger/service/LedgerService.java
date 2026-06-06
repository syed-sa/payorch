package com.payorch.ledger.service;

import com.payorch.model.Transaction;

public interface LedgerService {
    void recordEntry(Transaction transaction);
}
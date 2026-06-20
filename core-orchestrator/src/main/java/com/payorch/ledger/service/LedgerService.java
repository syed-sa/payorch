package com.payorch.ledger.service;

import com.payorch.shared.model.Transaction;

public interface LedgerService {
    void recordEntry(Transaction transaction);
}
package com.payorch.Ledger.service;

import com.payorch.Ledger.model.Transaction;

public interface LedgerService {
    void recordEntry(Transaction transaction);
}
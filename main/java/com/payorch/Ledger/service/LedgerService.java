package com.payorch.Ledger.service;

import com.payorch.Ledger.model.Transaction;
import java.util.UUID;

public interface LedgerService {
    void recordEntry(Transaction transaction);
}
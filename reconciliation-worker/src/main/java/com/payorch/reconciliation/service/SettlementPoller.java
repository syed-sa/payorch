package com.payorch.reconciliation.service;

public interface SettlementPoller {

    String providerName();

    void syncLatestWindow();
}

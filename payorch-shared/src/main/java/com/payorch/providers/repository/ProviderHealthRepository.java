// File: src/main/java/com/payorch/providers/repository/ProviderHealthRepository.java
package com.payorch.providers.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.payorch.model.ProviderHealth;

@Repository
public interface ProviderHealthRepository extends JpaRepository<ProviderHealth, String> {
}

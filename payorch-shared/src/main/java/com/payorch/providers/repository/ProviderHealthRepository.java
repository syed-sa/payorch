// File: src/main/java/com/payorch/providers/repository/ProviderHealthRepository.java
package com.payorch.providers.repository;

import com.payorch.providers.model.ProviderHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderHealthRepository extends JpaRepository<ProviderHealth, String> {
}

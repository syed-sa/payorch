package com.payorch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {
		"com.payorch.model",
		"com.payorch.orchestrator.model",
		"com.payorch.outbox.model"
})
@EnableJpaRepositories(basePackages = {
		"com.payorch.ledger.repository",
		"com.payorch.outbox.repository"
})
public class PayorchApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayorchApplication.class, args);
	}

}

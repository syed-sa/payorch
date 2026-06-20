package com.payorch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = {
		"com.payorch.ledger.repository",
		"com.payorch.outbox.repository"
})
public class PayorchApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayorchApplication.class, args);
	}

}

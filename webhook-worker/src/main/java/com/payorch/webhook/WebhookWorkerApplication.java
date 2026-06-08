package com.payorch.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {
        "com.payorch.model"
})
public class WebhookWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookWorkerApplication.class, args);
    }

}

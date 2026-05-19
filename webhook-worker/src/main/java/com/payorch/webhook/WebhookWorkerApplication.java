package com.payorch.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebhookWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookWorkerApplication.class, args);
    }

}

package com.payorch.webhook.controller;

import com.payorch.webhook.producer.WebhookEventProducer;
import com.payorch.webhook.validator.StripeWebhookValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookEventProducer webhookEventProducer;

    @MockBean
    private StripeWebhookValidator stripeWebhookValidator;

    private String testPayload;

    @BeforeEach
    void setUp() {
        testPayload = "{\"id\":\"evt_test_123\",\"type\":\"charge.succeeded\",\"data\":{\"object\":{\"id\":\"ch_test_123\"}}}";
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/webhooks/stripe/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("stripe-webhook"));
    }

    @Test
    void testHandleValidWebhook() throws Exception {
        when(stripeWebhookValidator.isValidSignature(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/webhooks/stripe")
                .contentType("application/json")
                .header("Stripe-Signature", "t=1234567890,v1=test_signature")
                .content(testPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        verify(webhookEventProducer, times(1)).publishStripeWebhook(anyString(), anyString());
    }

    @Test
    void testHandleInvalidSignature() throws Exception {
        when(stripeWebhookValidator.isValidSignature(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/webhooks/stripe")
                .contentType("application/json")
                .header("Stripe-Signature", "invalid_signature")
                .content(testPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid signature"));

        verify(webhookEventProducer, never()).publishStripeWebhook(anyString(), anyString());
    }

    @Test
    void testMissingSignatureHeader() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                .contentType("application/json")
                .content(testPayload))
                .andExpect(status().isBadRequest());
    }
}

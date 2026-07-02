// File: core-orchestrator/src/main/java/com/payorch/providers/webhook/WebhookParser.java
package com.payorch.webhook;

import com.payorch.shared.dto.NormalizedWebhookData;

public interface WebhookParser {
    String getSupportedProvider();
    NormalizedWebhookData parse(String rawJsonPayload);
}
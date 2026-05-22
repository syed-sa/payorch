// File: core-orchestrator/src/main/java/com/payorch/providers/webhook/WebhookParserFactory.java
package com.payorch.providers.webhook;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class WebhookParserFactory {

    private final Map<String, WebhookParser> parserRegistry;

    public WebhookParserFactory(List<WebhookParser> parsers) {
        this.parserRegistry = parsers.stream()
                .collect(Collectors.toMap(
                        parser -> parser.getSupportedProvider().toUpperCase(),
                        parser -> parser
                ));
    }

    public WebhookParser getParser(String provider) {
        return Optional.ofNullable(parserRegistry.get(provider.toUpperCase()))
                .orElseThrow(() -> new IllegalArgumentException("No registered parser strategy matches provider code: " + provider));
    }
}
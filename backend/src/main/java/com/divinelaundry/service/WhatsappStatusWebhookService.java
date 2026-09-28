package com.divinelaundry.service;

import com.divinelaundry.domain.WhatsappDeliveryStatus;
import com.divinelaundry.domain.WhatsappMessage;
import com.divinelaundry.repository.WhatsappMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsappStatusWebhookService {
    private static final Logger log = LoggerFactory.getLogger(WhatsappStatusWebhookService.class);
    private final WhatsappMessageRepository messages;

    public WhatsappStatusWebhookService(WhatsappMessageRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public boolean apply(String providerMessageId, WhatsappDeliveryStatus status, String timestamp, String error) {
        var stored = messages.findByProviderMessageId(providerMessageId);
        if (stored.isEmpty()) {
            log.info("WhatsApp status callback wamid={} status={} timestamp={} mapping=unknown error={}",
                maskProviderMessageId(providerMessageId), status, timestamp, status == WhatsappDeliveryStatus.FAILED ? error : null);
            return false;
        }
        WhatsappMessage message = stored.get();
        message.applyProviderStatus(status, error);
        messages.save(message);
        String orderNumber = message.getOrder() == null ? "unknown" : message.getOrder().getOrderNumber();
        log.info("WhatsApp status callback wamid={} status={} timestamp={} messageId={} orderNumber={} error={}",
            maskProviderMessageId(providerMessageId), status, timestamp, message.getId(), orderNumber,
            status == WhatsappDeliveryStatus.FAILED ? message.getLastError() : null);
        return true;
    }

    private static String maskProviderMessageId(String providerMessageId) {
        if (providerMessageId == null || providerMessageId.isBlank()) return "unknown";
        int visibleSuffixLength = Math.min(4, providerMessageId.length());
        return "***" + providerMessageId.substring(providerMessageId.length() - visibleSuffixLength);
    }
}
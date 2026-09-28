package com.divinelaundry.api;

import com.divinelaundry.domain.WhatsappDeliveryStatus;
import com.divinelaundry.service.WhatsappStatusWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsappWebhookController {
    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);
    private final WhatsappStatusWebhookService statuses;
    private final ObjectMapper objectMapper;
    private final String appSecret;
    private final String verifyToken;

    public WhatsappWebhookController(
            WhatsappStatusWebhookService statuses,
            ObjectMapper objectMapper,
            @Value("${app.whatsapp.app-secret:}") String appSecret,
            @Value("${app.whatsapp.webhook-verify-token:}") String verifyToken) {
        this.statuses = statuses;
        this.objectMapper = objectMapper;
        this.appSecret = appSecret;
        this.verifyToken = verifyToken;
        log.info("WhatsApp webhook configuration: app-secret={}, webhook-verify-token={}",
            presence(appSecret), presence(verifyToken));
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equals(mode) || !secureEquals(verifyToken, token) || challenge == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
    }

    @PostMapping
    public ResponseEntity<?> receive(
            @RequestBody(required = false) byte[] payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (payload == null || payload.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_payload"));
        }
        if (!verifySignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "invalid_signature"));
        }

        List<ProviderStatusEvent> events;
        try {
            events = parseStatusEvents(objectMapper.readTree(payload));
        } catch (Exception malformed) {
            return ResponseEntity.badRequest().body(Map.of("status", "malformed_event"));
        }

        int matched = 0;
        for (ProviderStatusEvent event : events) {
            if (statuses.apply(event.providerMessageId(), event.status(), event.timestamp(), event.error())) {
                matched++;
            }
        }
        return ResponseEntity.ok(Map.of("status", "accepted", "matched", matched));
    }

    static String computeSignature(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
    }

    private boolean verifySignature(byte[] payload, String signature) {
        if (!StringUtils.hasText(appSecret) || !StringUtils.hasText(signature)
                || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            String expected = computeSignature(payload, appSecret);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<ProviderStatusEvent> parseStatusEvents(JsonNode root) {
        if (root == null || !root.isObject() || !root.path("entry").isArray()) {
            throw new IllegalArgumentException("Malformed WhatsApp webhook");
        }
        List<ProviderStatusEvent> events = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            JsonNode changes = entry.path("changes");
            if (changes.isMissingNode()) continue;
            if (!changes.isArray()) throw new IllegalArgumentException("Malformed webhook changes");
            for (JsonNode change : changes) {
                JsonNode value = change.path("value");
                JsonNode providerStatuses = value.path("statuses");
                if (providerStatuses.isMissingNode()) continue;
                if (!providerStatuses.isArray()) throw new IllegalArgumentException("Malformed webhook statuses");
                for (JsonNode providerStatus : providerStatuses) {
                    String providerMessageId = providerStatus.path("id").asText(null);
                    String statusName = providerStatus.path("status").asText(null);
                    WhatsappDeliveryStatus status = statusFrom(statusName);
                    if (!StringUtils.hasText(providerMessageId) || status == null) {
                        throw new IllegalArgumentException("Malformed webhook status");
                    }
                    String timestamp = providerStatus.path("timestamp").asText(null);
                    if (!StringUtils.hasText(timestamp) || !timestamp.matches("\\d{1,13}")) {
                        throw new IllegalArgumentException("Malformed webhook timestamp");
                    }
                    events.add(new ProviderStatusEvent(
                            providerMessageId, status, timestamp, failureInfo(providerStatus)));
                }
            }
        }
        return events;
    }

    private static WhatsappDeliveryStatus statusFrom(String value) {
        if (!StringUtils.hasText(value)) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "sent" -> WhatsappDeliveryStatus.SENT;
            case "delivered" -> WhatsappDeliveryStatus.DELIVERED;
            case "read" -> WhatsappDeliveryStatus.READ;
            case "failed" -> WhatsappDeliveryStatus.FAILED;
            default -> null;
        };
    }

    private static String failureInfo(JsonNode providerStatus) {
        if (!"failed".equalsIgnoreCase(providerStatus.path("status").asText())) return null;
        JsonNode error = providerStatus.path("errors").path(0);
        String code = safeErrorPart(error.path("code").asText(null));
        String title = safeErrorPart(error.path("title").asText(null));
        String message = safeErrorPart(error.path("message").asText(null));
        String details = safeErrorPart(error.path("error_data").path("details").asText(null));
        StringBuilder description = new StringBuilder("Meta error");
        if (StringUtils.hasText(code)) description.append(" code=").append(code);
        if (StringUtils.hasText(title)) description.append(" title=").append(title);
        if (StringUtils.hasText(message)) description.append(" message=").append(message);
        if (StringUtils.hasText(details)) description.append(" details=").append(details);
        return description.toString();
    }

    private static String safeErrorPart(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return normalized.length() <= 150 ? normalized : normalized.substring(0, 147) + "...";
    }

    private static boolean secureEquals(String expected, String actual) {
        return StringUtils.hasText(expected) && StringUtils.hasText(actual)
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String presence(String value) {
        return StringUtils.hasText(value) ? "PRESENT" : "MISSING";
    }

    private record ProviderStatusEvent(
            String providerMessageId, WhatsappDeliveryStatus status, String timestamp, String error) {}
}
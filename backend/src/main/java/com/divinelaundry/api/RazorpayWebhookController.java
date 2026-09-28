package com.divinelaundry.api;

import com.divinelaundry.domain.PaymentRequest;
import com.divinelaundry.repository.PaymentRequestRepository;
import com.divinelaundry.service.PaymentRequestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class RazorpayWebhookController {
    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);
    private final PaymentRequestService paymentRequestService;
    private final PaymentRequestRepository paymentRequests;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public RazorpayWebhookController(
            PaymentRequestService paymentRequestService,
            PaymentRequestRepository paymentRequests,
            ObjectMapper objectMapper,
            @Value("${razorpay.webhook-secret:}") String webhookSecret) {
        this.paymentRequestService = paymentRequestService;
        this.paymentRequests = paymentRequests;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<?> handle(
            @RequestBody(required = false) byte[] payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) throws Exception {
        if (payload == null || payload.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_payload"));
        }
        if (!StringUtils.hasText(signature) || !verifySignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "invalid_signature"));
        }

        JsonNode root = objectMapper.readTree(payload);
        String event = root.path("event").asText(null);
        if (!"payment_link.paid".equalsIgnoreCase(event)) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        JsonNode payloadNode = root.path("payload");
        JsonNode paymentEntity = entityFrom(payloadNode, "payment");
        JsonNode paymentLinkEntity = entityFrom(payloadNode, "payment_link");
        JsonNode orderEntity = entityFrom(payloadNode, "order");
        if (paymentEntity.isMissingNode() || paymentEntity.isNull()) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        String paymentStatus = paymentEntity.path("status").asText(null);
        if (!"paid".equalsIgnoreCase(paymentStatus)
                && !"captured".equalsIgnoreCase(paymentStatus)
                && !"success".equalsIgnoreCase(paymentStatus)) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        String providerReference = firstNonBlank(
                textValue(paymentLinkEntity, "id"),
                textValue(paymentEntity, "payment_link_id"),
                textValue(paymentEntity, "link_id"),
                textValue(orderEntity, "payment_link_id"),
                textValue(orderEntity, "link_id"));
        if (!StringUtils.hasText(providerReference)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "missing_provider_reference"));
        }

        String referenceId = firstNonBlank(
                textValue(paymentLinkEntity, "reference_id"),
                textValue(paymentLinkEntity, "referenceId"),
                textValue(orderEntity, "receipt"),
                textValue(orderEntity, "reference_id"),
                textValue(orderEntity, "referenceId"),
                textValue(paymentEntity, "receipt"),
                textValue(paymentEntity, "reference_id"),
                textValue(paymentLinkEntity.path("notes"), "reference_id"),
                textValue(paymentLinkEntity.path("notes"), "referenceId"),
                textValue(orderEntity.path("notes"), "reference_id"),
                textValue(orderEntity.path("notes"), "referenceId"));
        if (!StringUtils.hasText(referenceId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "missing_reference"));
        }

        PaymentRequest request = paymentRequests.findByIdempotencyKey(referenceId).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "unknown_reference"));
        }

        String paymentLinkIdFromEntity = textValue(paymentEntity, "payment_link_id");
        if (StringUtils.hasText(paymentLinkIdFromEntity) && !providerReference.equals(paymentLinkIdFromEntity)) {
            log.warn("Razorpay payment rejected: payload provider reference mismatch requestId={} providerReference={}",
                    mask(request.getIdempotencyKey()), mask(providerReference));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "provider_reference_mismatch"));
        }
        if (StringUtils.hasText(request.getProviderReference())
                && !request.getProviderReference().equals(providerReference)) {
            log.warn("Razorpay payment rejected: stored provider reference mismatch requestId={} providerReference={}",
                    mask(request.getIdempotencyKey()), mask(providerReference));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "provider_reference_mismatch"));
        }

        BigDecimal paymentAmount = parseAmount(paymentEntity.path("amount"));
        BigDecimal paymentLinkAmount = parseAmount(paymentLinkEntity.path("amount"));
        BigDecimal orderAmount = parseAmount(orderEntity.path("amount"));
        BigDecimal amount = firstNonNull(paymentAmount, paymentLinkAmount, orderAmount);
        String currency = firstNonBlank(
                paymentEntity.path("currency").asText(null),
                paymentLinkEntity.path("currency").asText(null),
                orderEntity.path("currency").asText(null));
        if (amount == null || !StringUtils.hasText(currency)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "missing_payment_details"));
        }
        if (paymentAmount != null && paymentLinkAmount != null && paymentAmount.compareTo(paymentLinkAmount) != 0) {
            log.warn("Razorpay payment rejected: payment/link amount mismatch requestId={} providerReference={}",
                    mask(request.getIdempotencyKey()), mask(providerReference));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "amount_mismatch"));
        }
        if (paymentAmount != null && orderAmount != null && paymentAmount.compareTo(orderAmount) != 0) {
            log.warn("Razorpay payment rejected: payment/order amount mismatch requestId={} providerReference={}",
                    mask(request.getIdempotencyKey()), mask(providerReference));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "amount_mismatch"));
        }
        if (amount.compareTo(request.getRequestedAmount()) != 0) {
            log.warn("Razorpay payment rejected: amount mismatch requestId={} providerReference={}",
                    mask(request.getIdempotencyKey()), mask(providerReference));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "amount_mismatch"));
        }
        if (!request.getCurrency().equalsIgnoreCase(currency)) {
            log.warn("Razorpay payment rejected: request currency mismatch requestId={} providerReference={}",
                    mask(request.getIdempotencyKey()), mask(providerReference));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "currency_mismatch"));
        }
        if (!"INR".equalsIgnoreCase(currency)) {
            log.warn("Razorpay payment rejected: unsupported currency requestId={} providerReference={}",
                    mask(request.getIdempotencyKey()), mask(providerReference));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "currency_mismatch"));
        }

        String providerPaymentId = paymentEntity.path("id").asText(null);
        if (StringUtils.hasText(providerPaymentId)) {
            var duplicate = paymentRequests.findByProviderPaymentId(providerPaymentId);
            if (duplicate.isPresent() && !duplicate.get().getId().equals(request.getId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "duplicate_payment"));
            }
            if (duplicate.isPresent() && duplicate.get().getId().equals(request.getId())) {
                return ResponseEntity.ok(Map.of("status", "accepted"));
            }
        }

        if (!StringUtils.hasText(request.getProviderReference())) {
            request.setProviderReference(providerReference);
        }

        paymentRequestService.confirmVerifiedPayment(
                request.getOrderNumber(),
                providerReference,
                request.getRequestedAmount(),
                request.getCurrency(),
                "razorpay-webhook");

        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    static String computeSignature(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", value));
        }
        return sb.toString();
    }

    private boolean verifySignature(byte[] payload, String signature) {
        if (!StringUtils.hasText(webhookSecret) || !StringUtils.hasText(signature)) {
            return false;
        }
        try {
            String expected = computeSignature(payload, webhookSecret);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "unknown";
        int suffixLength = Math.min(4, value.length());
        return "***" + value.substring(value.length() - suffixLength);
    }

    private JsonNode entityFrom(JsonNode payloadNode, String fieldName) {
        if (payloadNode == null || payloadNode.isMissingNode() || payloadNode.isNull()) {
            return payloadNode == null ? null : payloadNode;
        }
        JsonNode candidate = payloadNode.path(fieldName);
        if (candidate != null && candidate.has("entity") && !candidate.path("entity").isMissingNode()) {
            return candidate.path("entity");
        }
        return candidate;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static BigDecimal firstNonNull(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText(null);
    }

    private BigDecimal parseAmount(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        long paise = node.asLong(-1L);
        if (paise < 0) {
            return null;
        }
        return BigDecimal.valueOf(paise, 2);
    }
}

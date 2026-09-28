package com.divinelaundry.api;

import com.divinelaundry.domain.WhatsappDeliveryStatus;
import com.divinelaundry.domain.WhatsappMessage;
import com.divinelaundry.repository.WhatsappMessageRepository;
import com.divinelaundry.service.WhatsappStatusWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsappWebhookControllerTest {
    private static final String APP_SECRET = "test-app-secret";
    private static final String VERIFY_TOKEN = "test-verify-token";

    @Test
    void sentEventKeepsMessageAtSent() throws Exception {
        WhatsappMessage message = sentMessage();
        Fixture fixture = fixture(message);

        ResponseEntity<?> response = fixture.send(payload("wamid.test", "sent"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.SENT);
    }

    @Test
    void deliveredEventUpdatesMessageWithoutCallingItRead() throws Exception {
        WhatsappMessage message = sentMessage();
        Fixture fixture = fixture(message);

        fixture.send(payload("wamid.test", "delivered"));

        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.DELIVERED);
    }

    @Test
    void readEventUpdatesMessageToRead() throws Exception {
        WhatsappMessage message = sentMessage();
        Fixture fixture = fixture(message);

        fixture.send(payload("wamid.test", "read"));

        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.READ);
    }

    @Test
    void failedEventStoresSafeProviderErrorInformation() throws Exception {
        WhatsappMessage message = sentMessage();
        Fixture fixture = fixture(message);
        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.test\","
            + "\"status\":\"failed\",\"timestamp\":\"1759000000\","
            + "\"errors\":[{\"code\":131049,\"title\":\"Ecosystem engagement protection\",\"message\":\"This message was not delivered to maintain healthy ecosystem engagement.\"," 
                + "\"error_data\":{\"details\":\"sensitive provider details\"}}]}]}}]}]}";

        fixture.send(payload);

        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.FAILED);
        assertThat(message.getLastError()).contains("131049", "Ecosystem engagement protection",
                "This message was not delivered to maintain healthy ecosystem engagement.",
            "sensitive provider details");
    }

    @Test
    void duplicateFailedCallbackPreservesTheOriginalDiagnostic() throws Exception {
        WhatsappMessage message = sentMessage();
        Fixture fixture = fixture(message);
        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.test\","
            + "\"status\":\"failed\",\"timestamp\":\"1759000000\","
            + "\"errors\":[{\"code\":131049,\"title\":\"Ecosystem protection\","
            + "\"message\":\"Not delivered\",\"error_data\":{\"details\":\"duplicate callback\"}}]}]}}]}]}";

        fixture.send(payload);
        String firstDiagnostic = message.getLastError();
        fixture.send(payload);

        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.FAILED);
        assertThat(message.getLastError()).isEqualTo(firstDiagnostic);
    }

    @Test
    void unknownProviderMessageIdIsAcknowledgedWithoutSaving() throws Exception {
        Fixture fixture = fixture(sentMessage());

        ResponseEntity<?> response = fixture.send(payload("wamid.unknown", "delivered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(fixture.repository, never()).save(org.mockito.ArgumentMatchers.any(WhatsappMessage.class));
    }

    @Test
    void duplicateStatusEventDoesNotRegressMessageState() throws Exception {
        WhatsappMessage message = sentMessage();
        Fixture fixture = fixture(message);
        String payload = payload("wamid.test", "read");

        fixture.send(payload);
        fixture.send(payload);
        fixture.send(payload("wamid.test", "sent"));

        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.READ);
    }

    @Test
    void malformedStatusEventIsRejectedWithoutUpdatingMessage() throws Exception {
        WhatsappMessage message = sentMessage();
        Fixture fixture = fixture(message);
        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"status\":\"delivered\"}]}}]}]}";

        ResponseEntity<?> response = fixture.send(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.SENT);
        verify(fixture.repository, never()).save(org.mockito.ArgumentMatchers.any(WhatsappMessage.class));
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        Fixture fixture = fixture(sentMessage());

        ResponseEntity<?> response = fixture.controller.receive(
                payload("wamid.invalid-signature", "delivered").getBytes(), "sha256=invalid");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

        @Test
        void exactRawBodyWithMatchingSignatureIsAccepted() throws Exception {
        Fixture fixture = fixture(sentMessage());
        byte[] rawBody = " {\"entry\":[{\"changes\":[]}] }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ResponseEntity<?> response = fixture.controller.receive(rawBody,
            WhatsappWebhookController.computeSignature(rawBody, APP_SECRET));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void signatureComputedWithWrongAppSecretIsRejected() throws Exception {
        Fixture fixture = fixture(sentMessage());
        byte[] rawBody = payload("wamid.wrong-secret", "delivered")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ResponseEntity<?> response = fixture.controller.receive(rawBody,
            WhatsappWebhookController.computeSignature(rawBody, "wrong-app-secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void modifiedRawBodyIsRejected() throws Exception {
        Fixture fixture = fixture(sentMessage());
        byte[] signedBody = payload("wamid.modified-body", "delivered")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] modifiedBody = payload("wamid.modified-body", "read")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ResponseEntity<?> response = fixture.controller.receive(modifiedBody,
            WhatsappWebhookController.computeSignature(signedBody, APP_SECRET));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

    @Test
    void webhookVerificationRequiresConfiguredToken() {
        Fixture fixture = fixture(sentMessage());

        assertThat(fixture.controller.verify("subscribe", VERIFY_TOKEN, "challenge").getBody())
                .isEqualTo("challenge");
        assertThat(fixture.controller.verify("subscribe", "wrong-token", "challenge").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static Fixture fixture(WhatsappMessage message) {
        WhatsappMessageRepository repository = mock(WhatsappMessageRepository.class);
        when(repository.findByProviderMessageId(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> message == null || !message.getProviderMessageId().equals(invocation.getArgument(0))
                ? Optional.empty() : Optional.of(message));
        WhatsappStatusWebhookService service = new WhatsappStatusWebhookService(repository);
        WhatsappWebhookController controller = new WhatsappWebhookController(
                service, new ObjectMapper(), APP_SECRET, VERIFY_TOKEN);
        return new Fixture(repository, controller);
    }

    private static WhatsappMessage sentMessage() {
        WhatsappMessage message = new WhatsappMessage("INVOICE_IMAGE:INV-1", null, "9000000000", "invoice");
        message.markPending();
        message.markSent(null, "wamid.test");
        return message;
    }

    private static String payload(String providerMessageId, String status) {
        return "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\""
            + providerMessageId + "\",\"status\":\"" + status
            + "\",\"timestamp\":\"1759000000\"}]}}]}]}";
    }

    private record Fixture(WhatsappMessageRepository repository, WhatsappWebhookController controller) {
        private ResponseEntity<?> send(String payload) throws Exception {
            byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return controller.receive(body, WhatsappWebhookController.computeSignature(body, APP_SECRET));
        }
    }
}
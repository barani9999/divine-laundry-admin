package com.divinelaundry.api;

import com.divinelaundry.domain.Customer;
import com.divinelaundry.domain.LaundryOrder;
import com.divinelaundry.domain.PaymentRequest;
import com.divinelaundry.domain.PaymentRequestStatus;
import com.divinelaundry.repository.PaymentRequestRepository;
import com.divinelaundry.service.PaymentRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RazorpayWebhookControllerTest {

    @Test
    void invalidWebhookSignatureIsRejected() throws Exception {
        PaymentRequestService service = Mockito.mock(PaymentRequestService.class);
        PaymentRequestRepository repository = Mockito.mock(PaymentRequestRepository.class);
        RazorpayWebhookController controller = new RazorpayWebhookController(service, repository, new ObjectMapper(), "webhook-secret");

        String payload = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_123\",\"reference_id\":\"req-1000\",\"amount\":100000,\"currency\":\"INR\"}},\"payment\":{\"entity\":{\"id\":\"pay_123\",\"amount\":100000,\"currency\":\"INR\",\"status\":\"captured\"}}}}";

        ResponseEntity<?> response = controller.handle(payload.getBytes(), "bad-signature");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).confirmVerifiedPayment(any(), any(), any(), any(), any());
    }

    @Test
    void validWebhookForUnknownReferenceIsRejected() throws Exception {
        PaymentRequestService service = Mockito.mock(PaymentRequestService.class);
        PaymentRequestRepository repository = Mockito.mock(PaymentRequestRepository.class);
        RazorpayWebhookController controller = new RazorpayWebhookController(service, repository, new ObjectMapper(), "webhook-secret");

        String payload = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_123\",\"reference_id\":\"req-unknown\",\"amount\":100000,\"currency\":\"INR\"}},\"payment\":{\"entity\":{\"id\":\"pay_123\",\"amount\":100000,\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        String signature = RazorpayWebhookController.computeSignature(payload.getBytes(), "webhook-secret");
        when(repository.findByIdempotencyKey("req-unknown")).thenReturn(java.util.Optional.empty());

        ResponseEntity<?> response = controller.handle(payload.getBytes(), signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(service, never()).confirmVerifiedPayment(any(), any(), any(), any(), any());
    }

    @Test
    void validWebhookFromRealPayloadShapeMapsReferenceToPaymentRequestAndConfirmsPayment() throws Exception {
        PaymentRequestService service = Mockito.mock(PaymentRequestService.class);
        PaymentRequestRepository repository = Mockito.mock(PaymentRequestRepository.class);
        RazorpayWebhookController controller = new RazorpayWebhookController(service, repository, new ObjectMapper(), "webhook-secret");

        Customer customer = new Customer("Test Customer", "9000000000", null, "Trichy");
        LaundryOrder order = new LaundryOrder("client-order-1000", customer, null, null, "admin");
        order.assignOrderNumber("SO-2026-000101");
        PaymentRequest request = new PaymentRequest(
                order,
                new BigDecimal("1000.00"),
                "INR",
                "razorpay",
                "admin",
                "req-1000");
        request.setStatus(PaymentRequestStatus.PENDING);
        request.setProviderReference("plink_123");
        request.setOrderNumber("SO-2026-000101");
        request.setProviderPaymentId(null);

        String payload = "{\"event\":\"payment_link.paid\",\"payload\":{\"order\":{\"entity\":{\"id\":\"order_123\",\"amount\":100000,\"currency\":\"INR\",\"receipt\":\"req-1000\",\"status\":\"paid\"}},\"payment\":{\"entity\":{\"id\":\"pay_123\",\"amount\":100000,\"currency\":\"INR\",\"status\":\"captured\",\"payment_link_id\":\"plink_123\",\"order_id\":\"order_123\"}},\"payment_link\":{\"entity\":{\"id\":\"plink_123\",\"amount\":100000,\"currency\":\"INR\",\"status\":\"paid\"}}}}";
        String signature = RazorpayWebhookController.computeSignature(payload.getBytes(), "webhook-secret");
        when(repository.findByIdempotencyKey("req-1000")).thenReturn(java.util.Optional.of(request));

        ResponseEntity<?> response = controller.handle(payload.getBytes(), signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).confirmVerifiedPayment(eq("SO-2026-000101"), eq("plink_123"), eq(new BigDecimal("1000.00")), eq("INR"), eq("razorpay-webhook"));
    }

        @Test
        void providerReferenceMismatchIsRejectedWithoutConfirmingPayment() throws Exception {
        PaymentRequestService service = Mockito.mock(PaymentRequestService.class);
        PaymentRequestRepository repository = Mockito.mock(PaymentRequestRepository.class);
        RazorpayWebhookController controller = new RazorpayWebhookController(service, repository,
            new ObjectMapper(), "webhook-secret");
        Customer customer = new Customer("Test Customer", "9000000000", null, "Trichy");
        LaundryOrder order = new LaundryOrder("client-order-reference-mismatch", customer, null, null, "admin");
        order.assignOrderNumber("SO-2026-000102");
        PaymentRequest request = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "razorpay",
            "admin", "req-reference-mismatch");
        request.setStatus(PaymentRequestStatus.PENDING);
        request.setProviderReference("plink_expected");
        String payload = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{"
            + "\"id\":\"plink_wrong\",\"reference_id\":\"req-reference-mismatch\","
            + "\"amount\":100000,\"currency\":\"INR\"}},\"payment\":{\"entity\":{"
            + "\"id\":\"pay_wrong\",\"amount\":100000,\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        when(repository.findByIdempotencyKey("req-reference-mismatch")).thenReturn(java.util.Optional.of(request));

        ResponseEntity<?> response = controller.handle(payload.getBytes(),
            RazorpayWebhookController.computeSignature(payload.getBytes(), "webhook-secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).confirmVerifiedPayment(any(), any(), any(), any(), any());
        }
}

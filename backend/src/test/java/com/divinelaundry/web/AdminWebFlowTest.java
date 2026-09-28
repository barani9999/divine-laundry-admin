package com.divinelaundry.web;

import com.divinelaundry.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:webtests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "app.admin.username=admin",
        "app.admin.password=TestPassword123!", "razorpay.webhook-secret=webhook-secret",
        "app.whatsapp.enabled=false", "app.payment.upi-id="})
@ActiveProfiles("test")
class AdminWebFlowTest {
    @Autowired WebApplicationContext context;
    @Autowired CustomerRepository customers;
    @Autowired LaundryOrderRepository orders;
    @Autowired LaundryServiceRepository catalog;
        @Autowired PaymentRepository payments;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void loginAndCsrfAreEnforced() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
                mvc.perform(get("/api/documents/orders/SO-2026-000001")).andExpect(status().is3xxRedirection());
                        mvc.perform(get("/orders/SO-2026-000001/invoice.pdf")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        mvc.perform(post("/customers").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        var result = mvc.perform(post("/login").with(csrf()).param("username", "admin").param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }

        @Test void dashboardRendersBusinessDateKpisOrdersAndCollectionActions() throws Exception {
                String dateContext = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy · EEEE", Locale.ENGLISH));
                var dashboard = mvc.perform(get("/").with(user("admin").roles("ADMIN")))
                                .andExpect(status().isOk())
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("Today's operations")))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString(dateContext)))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("Today's orders")))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ready for delivery")))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("Recent orders")))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("Collection health")))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("View outstanding")))
                                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/reports/outstanding\"")))
                                .andReturn();
                        String html = dashboard.getResponse().getContentAsString();
                        assertThat(html).contains("class=\"dashboard-kpi kpi-");
                        assertThat(html.split("class=\"dashboard-kpi kpi-", -1)).hasSize(7);
                        assertThat(html).doesNotContain(".00</strong>");
        }

                @Test void newOrderRendersSeparateQuantityPiecesEstimateAndActionColumns() throws Exception {
                        mvc.perform(get("/orders/new").with(user("admin").roles("ADMIN")))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string(org.hamcrest.Matchers.containsString(
                                                        "class=\"ticket-head\"><span>Service</span><span>Qty / KG</span><span>Pieces</span><span>Estimate</span><span>Action</span>")))
                                        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-target=\"quantity\"")))
                                        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-target=\"pieces\"")))
                                        .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"empty-ticket\"")))
                                        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-step=\"up\"")))
                                        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-step=\"down\"")));
                }

        @Test void reportsCenterAndReportRoutesRender() throws Exception {
                mvc.perform(get("/reports").with(user("admin").roles("ADMIN")))
                                .andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("Reports Center")))
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/reports/sales-overview\"")));
                for (String view : new String[] {"sales-overview", "daily-sales", "weekly-sales", "monthly-sales",
                                "service-sales", "category-sales", "top-services", "payments", "outstanding", "customers",
                                "order-status", "orders"}) {
                        mvc.perform(get("/reports/" + view).with(user("admin").roles("ADMIN")))
                                        .andExpect(status().isOk())
                            .andExpect(content().string(org.hamcrest.Matchers.containsString("Back to Reports")))
                                            .andExpect(content().string(org.hamcrest.Matchers.containsString("Download PDF")))
                            .andExpect(content().string(org.hamcrest.Matchers.containsString("M19 19v-7")));
                }
                mvc.perform(get("/reports/daily-sales").with(user("admin").roles("ADMIN")))
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("Average daily sales")));
                mvc.perform(get("/reports/service-sales").with(user("admin").roles("ADMIN")))
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("Total units")));
                mvc.perform(get("/reports/payments").with(user("admin").roles("ADMIN")))
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("Number of payments")));
                mvc.perform(get("/reports/orders").with(user("admin").roles("ADMIN")))
                        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Gross sales"))));
        }

        @Test void reportPdfRoutesDownloadValidPdfWithSelectedDateRange() throws Exception {
                for (String view : new String[] {"sales-overview", "daily-sales", "monthly-sales", "service-sales",
                                "payments", "outstanding", "orders"}) {
                        var response = mvc.perform(get("/reports/{view}/pdf", view)
                                                        .param("from", "2026-09-01").param("to", "2026-09-24")
                                                        .with(user("admin").roles("ADMIN")))
                                        .andExpect(status().isOk())
                                        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                                        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                                        .andReturn().getResponse();
                        assertThat(response.getContentAsByteArray()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
                }
                mvc.perform(get("/reports/daily-sales/pdf").param("from", "2026-09-01").param("to", "2026-09-24"))
                                .andExpect(status().is3xxRedirection());
        }

    @Test void whatsappRetryRequiresAdminAndCsrf() throws Exception {
        mvc.perform(post("/orders/unknown/whatsapp/1/retry"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/unknown/whatsapp/1/retry").with(user("staff")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/unknown/whatsapp/1/retry").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test void razorpayWebhookBypassesCsrfButStillValidatesSignature() throws Exception {
        String payload = "{\"event\":\"payment_link.paid\",\"payload\":{\"order\":{\"entity\":{\"id\":\"order_123\",\"amount\":10000,\"currency\":\"INR\",\"receipt\":\"rzp-test-SO-2026-000009-1000\",\"status\":\"paid\"}},\"payment\":{\"entity\":{\"id\":\"pay_123\",\"status\":\"paid\",\"amount\":10000,\"currency\":\"INR\",\"payment_link_id\":\"plink_123\",\"order_id\":\"order_123\"}},\"payment_link\":{\"entity\":{\"id\":\"plink_123\",\"amount\":10000,\"currency\":\"INR\",\"status\":\"paid\"}}}}";
        String validSignature = hmacSha256(payload, "webhook-secret");

        mvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Razorpay-Signature", validSignature))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Razorpay-Signature", "bad-signature"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/customers")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "Needs CSRF")
                        .param("phone", "9000000011")
                        .param("area", "Trichy")
                        .param("addressLine", "Test street"))
                .andExpect(status().isForbidden());
    }

    private static String hmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", value));
        }
        return sb.toString();
    }

    @Test void customerOrderInvoiceAndPaymentFlowUsesDatabase() throws Exception {
        String phone = "9" + String.format("%09d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1000000000L));
        long customersBefore = customers.count();
        mvc.perform(post("/customers").with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", "Test Customer").param("phone", "+91 " + phone).param("area", "Trichy").param("addressLine", "Test street"))
                .andExpect(status().is3xxRedirection());
        assertThat(customers.count()).isEqualTo(customersBefore + 1);
        var customer = customers.findByPhone(phone).orElseThrow();
        mvc.perform(post("/customers").with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", "Duplicate").param("phone", phone)).andExpect(view().name("customer-form"))
                .andExpect(model().attributeHasErrors("customerForm"));
        var service = catalog.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .filter(s -> s.getCode().equals("SHIRT_IRON")).findFirst().orElseThrow();
        String requestId = UUID.randomUUID().toString();
        long before = orders.count();
        String redirect = createOrder(customer.getId(), service.getId(), requestId);
        assertThat(createOrder(customer.getId(), service.getId(), requestId)).isEqualTo(redirect);
        assertThat(orders.count()).isEqualTo(before + 1);
        String number = redirect.substring("/orders/".length());
        var order = orders.findByOrderNumber(number).orElseThrow();
        assertThat(order.getTotal()).isEqualByComparingTo("28.00");
        assertThat(order.getInvoiceNumber()).isNotBlank();
        mvc.perform(get(redirect).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get(redirect + "/invoice").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(order.getInvoiceNumber())));
        mvc.perform(get(redirect + "/invoice.png").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        var firstPdf = mvc.perform(get(redirect + "/invoice.pdf").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(order.getInvoiceNumber() + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        var secondPdf = mvc.perform(get(redirect + "/invoice.pdf").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(firstPdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(secondPdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(firstPdf.length).isEqualTo(secondPdf.length);
        mvc.perform(post(redirect + "/payments").with(user("admin").roles("ADMIN")).with(csrf())
                .param("requestId", UUID.randomUUID().toString()).param("amount", "29.00").param("mode", "CASH"))
                .andExpect(model().attributeHasErrors("paymentForm"));
        mvc.perform(post(redirect + "/payments").with(user("admin").roles("ADMIN")).with(csrf())
                .param("requestId", UUID.randomUUID().toString()).param("amount", "10.00").param("mode", "CASH"))
                .andExpect(redirectedUrl(redirect));
        mvc.perform(get(redirect + "/invoice").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIAL")));
        String paymentId = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) mvc.perform(post(redirect + "/payments").with(user("admin").roles("ADMIN")).with(csrf())
                .param("requestId", paymentId).param("amount", "18.00").param("mode", "CASH"))
                .andExpect(redirectedUrl(redirect));
        assertThat(orders.findByOrderNumber(number).orElseThrow().getPaymentStatus().name()).isEqualTo("PAID");
        mvc.perform(post(redirect + "/status").with(user("admin").roles("ADMIN")).with(csrf()).param("status", "WASHING"))
                .andExpect(redirectedUrl(redirect));
        mvc.perform(post(redirect + "/status").with(user("admin").roles("ADMIN")).with(csrf()).param("status", "IRONING"))
                .andExpect(redirectedUrl(redirect));
        mvc.perform(post(redirect + "/status").with(user("admin").roles("ADMIN")).with(csrf()).param("status", "CLEANED"))
                .andExpect(redirectedUrl(redirect));
        mvc.perform(post(redirect + "/status").with(user("admin").roles("ADMIN")).with(csrf()).param("status", "READY"))
                .andExpect(redirectedUrl(redirect));
        mvc.perform(post(redirect + "/status").with(user("admin").roles("ADMIN")).with(csrf()).param("status", "DELIVERED"))
                .andExpect(redirectedUrl(redirect));
        mvc.perform(get(redirect + "/invoice").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get(redirect + "/invoice.pdf").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(createOrder(customer.getId(), service.getId(), UUID.randomUUID().toString())).isNotEqualTo(redirect);
        assertThat(orders.count()).isEqualTo(before + 2);
    }

    @Test void operationsListAndStatusActionsUseServerRulesAndCsrf() throws Exception {
        String phone = "9" + String.format("%09d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1000000000L));
        mvc.perform(post("/customers").with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", "Operations Customer").param("phone", phone).param("area", "Trichy")
                .param("addressLine", "Test street")).andExpect(status().is3xxRedirection());
        var customer = customers.findByPhone(phone).orElseThrow();
        var service = catalog.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .filter(item -> item.getCode().equals("SHIRT_IRON")).findFirst().orElseThrow();
        String redirect = createOrder(customer.getId(), service.getId(), UUID.randomUUID().toString());
        String number = redirect.substring("/orders/".length());

        mvc.perform(get("/orders").param("orderQuery", number).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(number)));
        mvc.perform(post("/orders/{number}/status", number).with(user("admin").roles("ADMIN"))
                .with(csrf()).param("status", "WASHING")).andExpect(redirectedUrl(redirect));
        assertThat(orders.findByOrderNumber(number).orElseThrow().getWorkStatus().name()).isEqualTo("WASHING");
        mvc.perform(post("/orders/{number}/status", number).with(user("admin").roles("ADMIN"))
                .with(csrf()).param("status", "DELIVERED")).andExpect(redirectedUrl(redirect));
        assertThat(orders.findByOrderNumber(number).orElseThrow().getWorkStatus().name()).isEqualTo("WASHING");
        mvc.perform(post("/orders/{number}/status", number).with(user("admin").roles("ADMIN"))
                .param("status", "CLEANED")).andExpect(status().isForbidden());
    }

    @Test void paymentReceiptRoutesAreAdminOnlyOrderScopedAndReadOnly() throws Exception {
        String phone = "9" + String.format("%09d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1000000000L));
        mvc.perform(post("/customers").with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", "Receipt Customer").param("phone", phone).param("area", "Trichy")
                .param("addressLine", "Receipt street")).andExpect(status().is3xxRedirection());
        var customer = customers.findByPhone(phone).orElseThrow();
        var service = catalog.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .filter(item -> item.getCode().equals("SHIRT_IRON")).findFirst().orElseThrow();
        String orderUrl = createOrder(customer.getId(), service.getId(), UUID.randomUUID().toString());
        String orderNumber = orderUrl.substring("/orders/".length());
        String requestId = UUID.randomUUID().toString();
        mvc.perform(post(orderUrl + "/payments").with(user("admin").roles("ADMIN")).with(csrf())
                .param("requestId", requestId).param("amount", "10.00").param("mode", "CASH")
                .param("reference", "RECEIPT-REF")).andExpect(redirectedUrl(orderUrl));
        var payment = payments.findByClientRequestId(requestId).orElseThrow();
        long paymentCount = payments.count();
        String receiptUrl = "/orders/" + orderNumber + "/payments/" + payment.getPaymentNumber() + "/receipt";
        mvc.perform(get(receiptUrl)).andExpect(status().is3xxRedirection());
        mvc.perform(get(receiptUrl).with(user("cashier").roles("CASHIER"))).andExpect(status().isForbidden());
        mvc.perform(get(receiptUrl).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(payment.getPaymentNumber())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIAL")));
        var firstReceiptPdf = mvc.perform(get(receiptUrl + ".pdf").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(payment.getPaymentNumber())))
                .andReturn().getResponse().getContentAsByteArray();
        var secondReceiptPdf = mvc.perform(get(receiptUrl + ".pdf").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(firstReceiptPdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(secondReceiptPdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(payments.count()).isEqualTo(paymentCount);
        mvc.perform(get("/orders/OTHER-ORDER/payments/" + payment.getPaymentNumber() + "/receipt")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test void restOrderUsesAuthenticatedActorAndCustomerPhoneNormalization() throws Exception {
        String digits = "9" + String.format("%09d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1000000000L));
        mvc.perform(post("/api/customers").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"name\":\"REST Customer\",\"phone\":\"+91 %s\",\"addressLine\":\"Test\",\"area\":\"Trichy\"}").formatted(digits)))
                .andExpect(status().isCreated());
        assertThat(customers.findByPhone(digits)).isPresent();

        var customer = customers.findByPhone(digits).orElseThrow();
        var service = catalog.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .filter(item -> item.getCode().equals("SHIRT_IRON")).findFirst().orElseThrow();
        String requestId = UUID.randomUUID().toString();
        String body = ("{\"clientRequestId\":\"%s\",\"customerId\":%d,\"deliveryAt\":\"2027-01-15T18:00:00Z\",\"notes\":\"REST\",\"createdBy\":\"spoofed\",\"discount\":0,\"tax\":0,\"items\":[{\"serviceId\":%d,\"billableQuantity\":2,\"pieceCount\":2,\"noPrint\":false}]}")
                .formatted(requestId, customer.getId(), service.getId());
        var result = mvc.perform(post("/api/orders").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String orderNumber = result.getResponse().getContentAsString()
                .replaceAll(".*\\\"orderNumber\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getCreatedBy()).isEqualTo("admin");
    }

    private String createOrder(Long customerId, Long serviceId, String requestId) throws Exception {
        return mvc.perform(post("/orders").with(user("admin").roles("ADMIN")).with(csrf())
                .param("requestId", requestId).param("customerId", customerId.toString()).param("deliveryAt", "2027-01-15T18:00")
                .param("items[0].serviceId", serviceId.toString()).param("items[0].quantity", "2").param("items[0].pieces", "2")
                .param("discount", "0").param("tax", "0"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
    }
}

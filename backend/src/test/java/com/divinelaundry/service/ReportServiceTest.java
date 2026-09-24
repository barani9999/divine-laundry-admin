package com.divinelaundry.service;

import com.divinelaundry.domain.Customer;
import com.divinelaundry.domain.LaundryOrder;
import com.divinelaundry.domain.LaundryServiceItem;
import com.divinelaundry.domain.OrderItem;
import com.divinelaundry.domain.OrderStatus;
import com.divinelaundry.domain.Payment;
import com.divinelaundry.domain.PricingUnit;
import com.divinelaundry.repository.LaundryOrderRepository;
import com.divinelaundry.repository.LaundryServiceRepository;
import com.divinelaundry.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final LaundryServiceRepository services = mock(LaundryServiceRepository.class);
    private final ReportService reports = new ReportService(orders, payments, services, "Asia/Kolkata");

    @Test
    void aggregatesSalesPaymentsCategoriesCustomersAndPeriods() {
        LaundryServiceItem shirt = new LaundryServiceItem("SHIRT", "Shirt", "Ironing", PricingUnit.PIECE,
                new BigDecimal("50.00"));
        when(services.findAll()).thenReturn(List.of(shirt));
        LaundryOrder first = order(1L, "SO-1", "INV-1", "Asha", "9000000001", "2026-09-01T05:00:00Z", 100, 2, shirt);
        LaundryOrder second = order(2L, "SO-2", "INV-2", "Bala", "9000000002", "2026-09-08T05:00:00Z", 50, 1, shirt);
        Payment payment = payment(1L, first, "75.00");
        when(orders.findSalesOrdersBetween(any(), any(), any())).thenReturn(List.of(first, second));
        when(payments.findByOrder_IdInOrderByPaidAtDesc(anyCollection())).thenReturn(List.of(payment));

        ReportService.ReportData report = reports.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(report.grossSales()).isEqualByComparingTo("150.00");
        assertThat(report.collected()).isEqualByComparingTo("75.00");
        assertThat(report.outstanding()).isEqualByComparingTo("75.00");
        assertThat(report.paidOrders()).isZero();
        assertThat(report.partialOrders()).isEqualTo(1);
        assertThat(report.unpaidOrders()).isEqualTo(1);
        assertThat(report.services()).singleElement().satisfies(row -> {
            assertThat(row.sales()).isEqualByComparingTo("150.00");
            assertThat(row.orders()).isEqualTo(2);
            assertThat(row.quantity()).isEqualByComparingTo("3");
        });
        assertThat(report.categoriesSales()).singleElement().extracting(ReportService.CategoryRow::category)
                .isEqualTo("Ironing");
        assertThat(report.customers()).hasSize(2);
        assertThat(report.daily()).hasSize(2);
        assertThat(report.weekly()).hasSize(2);
        assertThat(report.monthly()).hasSize(1);
        assertThat(report.payments()).singleElement().satisfies(row -> assertThat(row.amount()).isEqualByComparingTo("75.00"));
        verify(orders).findSalesOrdersBetween(any(), any(), any());
    }

    @Test
    void rejectsInvalidDateRangeBeforeQuerying() {
        assertThatThrownBy(() -> reports.between(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on or after");
        verifyNoInteractions(orders, payments, services);
    }

    @Test
    void fullyPaidOrderHasNoOutstandingBalance() {
        LaundryServiceItem service = new LaundryServiceItem("SHIRT", "Shirt", "Ironing", PricingUnit.PIECE,
                new BigDecimal("50.00"));
        LaundryOrder order = order(3L, "SO-3", "INV-3", "Chitra", "9000000003", "2026-09-12T05:00:00Z", 50, 1, service);
        when(services.findAll()).thenReturn(List.of(service));
        when(orders.findSalesOrdersBetween(any(), any(), any())).thenReturn(List.of(order));
        Payment payment = payment(2L, order, "50.00");
        when(payments.findByOrder_IdInOrderByPaidAtDesc(anyCollection())).thenReturn(List.of(payment));

        ReportService.ReportData report = reports.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(report.outstanding()).isZero();
        assertThat(report.paidOrders()).isEqualTo(1);
        assertThat(report.orderRows()).singleElement().satisfies(row ->
            assertThat(row.outstanding()).isEqualByComparingTo("0.00"));
    }

    private static LaundryOrder order(long id, String number, String invoice, String customerName, String phone,
            String placedAt, int rate, int quantity, LaundryServiceItem service) {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(id);
        when(customer.getName()).thenReturn(customerName);
        when(customer.getPhone()).thenReturn(phone);
        LaundryOrder order = mock(LaundryOrder.class);
        when(order.getId()).thenReturn(id);
        when(order.getOrderNumber()).thenReturn(number);
        when(order.getInvoiceNumber()).thenReturn(invoice);
        when(order.getCustomer()).thenReturn(customer);
        when(order.getPlacedAt()).thenReturn(Instant.parse(placedAt));
        when(order.getWorkStatus()).thenReturn(OrderStatus.READY);
        when(order.getTotal()).thenReturn(BigDecimal.valueOf(rate));
        OrderItem item = new OrderItem(service, BigDecimal.valueOf(quantity), quantity, false);
        when(order.getItems()).thenReturn(List.of(item));
        return order;
    }

    private static Payment payment(long id, LaundryOrder order, String amount) {
        Payment payment = mock(Payment.class);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(new BigDecimal(amount));
        when(payment.getPaidAt()).thenReturn(Instant.parse("2026-09-15T05:00:00Z"));
        when(payment.getTransactionReference()).thenReturn("REF-" + id);
        return payment;
    }

}
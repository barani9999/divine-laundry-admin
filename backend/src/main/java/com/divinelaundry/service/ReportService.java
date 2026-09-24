package com.divinelaundry.service;

import com.divinelaundry.domain.LaundryOrder;
import com.divinelaundry.domain.OrderItem;
import com.divinelaundry.domain.OrderStatus;
import com.divinelaundry.domain.Payment;
import com.divinelaundry.domain.PaymentStatus;
import com.divinelaundry.repository.LaundryOrderRepository;
import com.divinelaundry.repository.LaundryServiceRepository;
import com.divinelaundry.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private static final Set<OrderStatus> EXCLUDED = Set.of(OrderStatus.DRAFT, OrderStatus.CANCELLED);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final LaundryOrderRepository orders;
    private final PaymentRepository payments;
    private final LaundryServiceRepository services;
    private final ZoneId zone;

    public ReportService(LaundryOrderRepository orders, PaymentRepository payments,
            LaundryServiceRepository services, @Value("${app.business-zone:Asia/Kolkata}") String zone) {
        this.orders = orders;
        this.payments = payments;
        this.services = services;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public ReportData between(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Report end date must be on or after start date");
        }
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<LaundryOrder> rows = orders.findSalesOrdersBetween(start, end, EXCLUDED);
        Map<Long, BigDecimal> paidByOrder = paidByOrder(rows);
        Map<String, String> categories = services.findAll().stream()
                .collect(Collectors.toMap(item -> item.getCode(), item -> item.getCategory(), (first, ignored) -> first));
        return new ReportData(from, to, rows, paidByOrder, categories,
                serviceRows(rows, categories), categoryRows(rows, categories), customerRows(rows, paidByOrder),
                statusRows(rows, paidByOrder), dailyRows(rows, paidByOrder), weeklyRows(rows, paidByOrder),
                monthlyRows(rows, paidByOrder), paymentRows(rows, paidByOrder));
    }

    private Map<Long, BigDecimal> paidByOrder(List<LaundryOrder> rows) {
        List<Long> ids = rows.stream().map(LaundryOrder::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) return Map.of();
        return payments.findByOrder_IdInOrderByPaidAtDesc(ids).stream()
                .collect(Collectors.groupingBy(payment -> payment.getOrder().getId(),
                        Collectors.reducing(ZERO, Payment::getAmount, BigDecimal::add)));
    }

    private List<ServiceRow> serviceRows(List<LaundryOrder> rows, Map<String, String> categories) {
        Map<String, ServiceAccumulator> grouped = new LinkedHashMap<>();
        rows.forEach(order -> order.getItems().forEach(item -> {
            ServiceAccumulator row = grouped.computeIfAbsent(item.getServiceCode(), ignored ->
                    new ServiceAccumulator(item.getServiceCode(), item.getServiceName(), categories.getOrDefault(item.getServiceCode(), "Uncategorised")));
            row.sales = row.sales.add(item.getLineTotal());
            row.quantity = row.quantity.add(item.getBillableQuantity());
            row.pieces += item.getPieceCount();
            row.orders.add(order.getId());
        }));
        return grouped.values().stream().map(ServiceAccumulator::toRow)
                .sorted(Comparator.comparing(ServiceRow::sales).reversed()).toList();
    }

    private List<CategoryRow> categoryRows(List<LaundryOrder> rows, Map<String, String> categories) {
        Map<String, CategoryAccumulator> grouped = new LinkedHashMap<>();
        rows.forEach(order -> order.getItems().forEach(item -> {
            String category = categories.getOrDefault(item.getServiceCode(), "Uncategorised");
            CategoryAccumulator row = grouped.computeIfAbsent(category, CategoryAccumulator::new);
            row.sales = row.sales.add(item.getLineTotal());
            row.quantity = row.quantity.add(item.getBillableQuantity());
            row.orders.add(order.getId());
        }));
        BigDecimal total = grouped.values().stream().map(row -> row.sales).reduce(ZERO, BigDecimal::add);
        return grouped.values().stream().map(row -> row.toRow(total))
                .sorted(Comparator.comparing(CategoryRow::sales).reversed()).toList();
    }

    private List<CustomerRow> customerRows(List<LaundryOrder> rows, Map<Long, BigDecimal> paidByOrder) {
        Map<Long, CustomerAccumulator> grouped = new LinkedHashMap<>();
        rows.forEach(order -> {
            CustomerAccumulator row = grouped.computeIfAbsent(order.getCustomer().getId(), ignored ->
                    new CustomerAccumulator(order.getCustomer().getName(), order.getCustomer().getPhone()));
            row.orders++;
            row.sales = row.sales.add(order.getTotal());
            row.paid = row.paid.add(paidByOrder.getOrDefault(order.getId(), ZERO));
        });
        return grouped.values().stream().map(CustomerAccumulator::toRow)
                .sorted(Comparator.comparing(CustomerRow::sales).reversed()).toList();
    }

    private List<StatusRow> statusRows(List<LaundryOrder> rows, Map<Long, BigDecimal> paidByOrder) {
        Map<OrderStatus, StatusAccumulator> grouped = new EnumMap<>(OrderStatus.class);
        rows.forEach(order -> {
            StatusAccumulator row = grouped.computeIfAbsent(order.getWorkStatus(), StatusAccumulator::new);
            row.orders++;
            row.sales = row.sales.add(order.getTotal());
            row.paid = row.paid.add(paidByOrder.getOrDefault(order.getId(), ZERO));
        });
        return grouped.values().stream().map(StatusAccumulator::toRow)
                .sorted(Comparator.comparing(StatusRow::status)).toList();
    }

    private List<PeriodRow> dailyRows(List<LaundryOrder> rows, Map<Long, BigDecimal> paidByOrder) {
        return periodRows(rows, paidByOrder, order -> LocalDate.ofInstant(order.getPlacedAt(), zone),
                date -> date.toString());
    }

    private List<PeriodRow> weeklyRows(List<LaundryOrder> rows, Map<Long, BigDecimal> paidByOrder) {
        return periodRows(rows, paidByOrder, order -> LocalDate.ofInstant(order.getPlacedAt(), zone)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), date -> "Week of " + date);
    }

    private List<PeriodRow> monthlyRows(List<LaundryOrder> rows, Map<Long, BigDecimal> paidByOrder) {
        return periodRows(rows, paidByOrder, order -> LocalDate.ofInstant(order.getPlacedAt(), zone).withDayOfMonth(1),
                date -> YearMonth.from(date).toString());
    }

    private List<PeriodRow> periodRows(List<LaundryOrder> rows, Map<Long, BigDecimal> paidByOrder,
            Function<LaundryOrder, LocalDate> keyFunction, Function<LocalDate, String> labelFunction) {
        Map<LocalDate, PeriodAccumulator> grouped = new LinkedHashMap<>();
        rows.forEach(order -> {
            LocalDate key = keyFunction.apply(order);
            PeriodAccumulator row = grouped.computeIfAbsent(key, ignored -> new PeriodAccumulator(key, labelFunction.apply(key)));
            row.add(order, paidByOrder.getOrDefault(order.getId(), ZERO));
        });
        return grouped.values().stream().map(PeriodAccumulator::toRow).toList();
    }

    private List<PaymentRow> paymentRows(List<LaundryOrder> rows, Map<Long, BigDecimal> paidByOrder) {
        Set<Long> ids = rows.stream().map(LaundryOrder::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) return List.of();
        return payments.findByOrder_IdInOrderByPaidAtDesc(ids).stream().map(payment -> {
            LaundryOrder order = payment.getOrder();
            return new PaymentRow(payment.getPaidAt(), order.getOrderNumber(), order.getInvoiceNumber(),
                    order.getCustomer().getName(), order.getTotal(), payment.getAmount(),
                    paymentStatus(order, paidByOrder.getOrDefault(order.getId(), ZERO)), payment.getTransactionReference());
        }).toList();
    }

    private static PaymentStatus paymentStatus(LaundryOrder order, BigDecimal paid) {
        if (paid.signum() == 0) return PaymentStatus.UNPAID;
        return paid.compareTo(order.getTotal()) < 0 ? PaymentStatus.PARTIAL : PaymentStatus.PAID;
    }

    public record ReportData(LocalDate from, LocalDate to, List<LaundryOrder> orders,
            Map<Long, BigDecimal> paidByOrder, Map<String, String> categories,
            List<ServiceRow> services, List<CategoryRow> categoriesSales, List<CustomerRow> customers,
            List<StatusRow> statuses, List<PeriodRow> daily, List<PeriodRow> weekly,
            List<PeriodRow> monthly, List<PaymentRow> payments) {
        public BigDecimal grossSales() { return orders.stream().map(LaundryOrder::getTotal).reduce(ZERO, BigDecimal::add); }
        public long orderCount() { return orders.size(); }
        public BigDecimal averageBill() { return orderCount() == 0 ? ZERO : grossSales().divide(BigDecimal.valueOf(orderCount()), 2, RoundingMode.HALF_UP); }
        public BigDecimal collected() { return paidByOrder.values().stream().reduce(ZERO, BigDecimal::add); }
        public BigDecimal outstanding() { return grossSales().subtract(collected()).max(ZERO); }
        public long paidOrders() { return orders.stream().filter(order -> paid(order).compareTo(order.getTotal()) >= 0).count(); }
        public long partialOrders() { return orders.stream().filter(order -> { BigDecimal paid = paid(order); return paid.signum() > 0 && paid.compareTo(order.getTotal()) < 0; }).count(); }
        public long unpaidOrders() { return orders.stream().filter(order -> paid(order).signum() == 0).count(); }
        public long paymentCount() { return payments.size(); }
        public BigDecimal averagePayment() { return paymentCount() == 0 ? ZERO : collected().divide(BigDecimal.valueOf(paymentCount()), 2, RoundingMode.HALF_UP); }
        public long categoriesWithSales() { return categoriesSales.stream().filter(row -> row.sales().signum() > 0).count(); }
        public long customersWithBalance() { return customers.stream().filter(row -> row.outstanding().signum() > 0).count(); }
        public long ordersWithBalance() { return orderRows().stream().filter(row -> row.outstanding().signum() > 0).count(); }
        public BigDecimal largestOutstanding() { return outstandingRows().stream().map(OrderRow::outstanding).max(BigDecimal::compareTo).orElse(ZERO); }
        public String topService() { return services.isEmpty() ? "None" : services.get(0).name(); }
        public String topCategory() { return categoriesSales.isEmpty() ? "None" : categoriesSales.get(0).category(); }
        public String bestPeriod(List<PeriodRow> rows) { return rows.stream().max(Comparator.comparing(PeriodRow::sales)).map(PeriodRow::label).orElse("None"); }
        public BigDecimal averagePeriodSales(List<PeriodRow> rows) { return rows.isEmpty() ? ZERO : grossSales().divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP); }
        public BigDecimal totalUnits() { return services.stream().map(ServiceRow::quantity).reduce(ZERO, BigDecimal::add); }
        public BigDecimal salesPercent(BigDecimal value) { return grossSales().signum() == 0 ? ZERO : value.multiply(BigDecimal.valueOf(100)).divide(grossSales(), 2, RoundingMode.HALF_UP); }
        public List<OrderRow> orderRows() { return orders.stream().map(order -> new OrderRow(order, paid(order))).toList(); }
        public List<OrderRow> outstandingRows() { return orderRows().stream().filter(row -> row.outstanding().signum() > 0)
            .sorted(Comparator.comparing(OrderRow::outstanding).reversed()).toList(); }
        private BigDecimal paid(LaundryOrder order) { return paidByOrder.getOrDefault(order.getId(), ZERO); }
    }

    public record ServiceRow(String code, String name, String category, BigDecimal sales, long orders, BigDecimal quantity, long pieces) {}
    public record CategoryRow(String category, BigDecimal sales, long orders, BigDecimal quantity, BigDecimal share) {}
    public record CustomerRow(String name, String phone, long orders, BigDecimal sales, BigDecimal paid, BigDecimal outstanding, BigDecimal averageBill) {}
    public record StatusRow(OrderStatus status, long orders, BigDecimal sales, BigDecimal paid, BigDecimal outstanding) {}
    public record PeriodRow(LocalDate period, String label, long orders, BigDecimal sales, BigDecimal collected, BigDecimal outstanding, BigDecimal averageBill) {}
    public record PaymentRow(Instant paidAt, String orderNumber, String invoiceNumber, String customer, BigDecimal orderTotal, BigDecimal amount, PaymentStatus status, String reference) {}
    public record OrderRow(LaundryOrder order, BigDecimal paid) { public BigDecimal outstanding() { return order.getTotal().subtract(paid).max(ZERO); } }

    private static final class ServiceAccumulator {
        private final String code; private final String name; private final String category;
        private BigDecimal sales = ZERO; private BigDecimal quantity = ZERO; private long pieces; private final Set<Long> orders = new java.util.HashSet<>();
        private ServiceAccumulator(String code, String name, String category) { this.code = code; this.name = name; this.category = category; }
        private ServiceRow toRow() { return new ServiceRow(code, name, category, sales, orders.size(), quantity, pieces); }
    }
    private static final class CategoryAccumulator {
        private final String category; private BigDecimal sales = ZERO; private BigDecimal quantity = ZERO; private final Set<Long> orders = new java.util.HashSet<>();
        private CategoryAccumulator(String category) { this.category = category; }
        private CategoryRow toRow(BigDecimal total) { BigDecimal share = total.signum() == 0 ? ZERO : sales.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP); return new CategoryRow(category, sales, orders.size(), quantity, share); }
    }
    private static final class CustomerAccumulator {
        private final String name; private final String phone; private long orders; private BigDecimal sales = ZERO; private BigDecimal paid = ZERO;
        private CustomerAccumulator(String name, String phone) { this.name = name; this.phone = phone; }
        private CustomerRow toRow() { return new CustomerRow(name, phone, orders, sales, paid, sales.subtract(paid).max(ZERO), orders == 0 ? ZERO : sales.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP)); }
    }
    private static final class StatusAccumulator {
        private final OrderStatus status; private long orders; private BigDecimal sales = ZERO; private BigDecimal paid = ZERO;
        private StatusAccumulator(OrderStatus status) { this.status = status; }
        private StatusRow toRow() { return new StatusRow(status, orders, sales, paid, sales.subtract(paid).max(ZERO)); }
    }
    private static final class PeriodAccumulator {
        private final LocalDate period; private final String label; private long orders; private BigDecimal sales = ZERO; private BigDecimal collected = ZERO;
        private PeriodAccumulator(LocalDate period, String label) { this.period = period; this.label = label; }
        private void add(LaundryOrder order, BigDecimal paid) { orders++; sales = sales.add(order.getTotal()); collected = collected.add(paid); }
        private PeriodRow toRow() { return new PeriodRow(period, label, orders, sales, collected, sales.subtract(collected).max(ZERO), orders == 0 ? ZERO : sales.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP)); }
    }
}
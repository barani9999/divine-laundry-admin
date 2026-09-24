package com.divinelaundry.repository;

import com.divinelaundry.domain.LaundryOrder;
import com.divinelaundry.domain.OrderStatus;
import com.divinelaundry.domain.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LaundryOrderRepository extends JpaRepository<LaundryOrder, Long> {
    @EntityGraph(attributePaths = {"customer"})
    List<LaundryOrder> findTop50ByOrderByPlacedAtDesc();
    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<LaundryOrder> findByClientRequestId(String clientRequestId);

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<LaundryOrder> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"customer", "items"})
    List<LaundryOrder> findByCustomerIdAndWorkStatusInOrderByPlacedAtDesc(Long customerId, Collection<OrderStatus> statuses);
        @EntityGraph(attributePaths = {"customer", "items"})
        List<LaundryOrder> findByCustomerIdOrderByPlacedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "items"})
    List<LaundryOrder> findByWorkStatusInOrderByDeliveryAtAsc(Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"customer", "items"})
    @Query("""
            select distinct o from LaundryOrder o
            where o.placedAt >= :startAt and o.placedAt < :endAt
              and o.workStatus not in :excluded
            order by o.placedAt asc
            """)
    List<LaundryOrder> findSalesOrdersBetween(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excluded") Collection<OrderStatus> excluded);
    @EntityGraph(attributePaths = {"customer", "items"})
    @Query("""
            select distinct o from LaundryOrder o join o.customer c
            where (:orderQuery = '' or lower(o.orderNumber) like lower(concat('%', :orderQuery, '%')))
              and (:customerQuery = '' or lower(c.name) like lower(concat('%', :customerQuery, '%')))
              and (:phoneQuery = '' or c.phone like concat('%', :phoneQuery, '%'))
              and (:status is null or o.workStatus = :status)
              and (:paymentStatus is null or o.paymentStatus = :paymentStatus)
              and (:placedFrom is null or o.placedAt >= :placedFrom)
              and (:placedTo is null or o.placedAt < :placedTo)
              and (:pickupFrom is null or o.pickupAt >= :pickupFrom)
              and (:pickupTo is null or o.pickupAt < :pickupTo)
              and (:deliveryFrom is null or o.deliveryAt >= :deliveryFrom)
              and (:deliveryTo is null or o.deliveryAt < :deliveryTo)
            """)
    Page<LaundryOrder> findOperations(
            @Param("orderQuery") String orderQuery,
            @Param("customerQuery") String customerQuery,
            @Param("phoneQuery") String phoneQuery,
            @Param("status") OrderStatus status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("placedFrom") Instant placedFrom,
            @Param("placedTo") Instant placedTo,
            @Param("pickupFrom") Instant pickupFrom,
            @Param("pickupTo") Instant pickupTo,
            @Param("deliveryFrom") Instant deliveryFrom,
            @Param("deliveryTo") Instant deliveryTo,
            Pageable pageable);
    long countByWorkStatus(OrderStatus status);
    long countByWorkStatusIn(Collection<OrderStatus> statuses);
    long countByPickupAtBetweenAndWorkStatusIn(Instant start, Instant end, Collection<OrderStatus> statuses);
    long countByDeliveryAtBetweenAndWorkStatusIn(Instant start, Instant end, Collection<OrderStatus> statuses);
    long countByDeliveryAtBeforeAndWorkStatusIn(Instant deliveryAt, Collection<OrderStatus> statuses);
    long countByPaymentStatusInAndWorkStatusNotIn(
            Collection<PaymentStatus> paymentStatuses,
            Collection<OrderStatus> excludedStatuses);

    @Query("""
            select coalesce(sum(o.total), 0) from LaundryOrder o
            where o.paymentStatus in :paymentStatuses
              and o.workStatus not in :excludedStatuses
            """)
    BigDecimal sumUnpaidBilledAmount(
            @Param("paymentStatuses") Collection<PaymentStatus> paymentStatuses,
            @Param("excludedStatuses") Collection<OrderStatus> excludedStatuses);

    @Query("""
            select coalesce(sum(o.total), 0) from LaundryOrder o
            where o.placedAt >= :startAt and o.placedAt < :endAt
              and o.workStatus not in :excluded
            """)
    BigDecimal sumSalesBetween(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excluded") Collection<OrderStatus> excluded);

    @Query("""
            select count(o) from LaundryOrder o
            where o.placedAt >= :startAt and o.placedAt < :endAt
              and o.workStatus not in :excluded
            """)
    long countSalesOrdersBetween(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excluded") Collection<OrderStatus> excluded);

    @Query("""
            select new com.divinelaundry.repository.ServiceSalesRow(
                i.serviceCode, i.serviceName, sum(i.lineTotal), count(distinct o.id),
                sum(i.billableQuantity), sum(i.pieceCount))
            from LaundryOrder o join o.items i
            where o.placedAt >= :startAt and o.placedAt < :endAt
              and o.workStatus not in :excluded
            group by i.serviceCode, i.serviceName
            order by sum(i.lineTotal) desc
            """)
    List<ServiceSalesRow> serviceSalesBetween(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excluded") Collection<OrderStatus> excluded);
}

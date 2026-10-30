package com.example.platform.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * Orders that have sat in {@code status} since before {@code cutoff}. SKIP LOCKED lets several
     * order-service instances sweep concurrently without ever handling the same order twice.
     */
    @Query(value = """
            SELECT * FROM orders
            WHERE status = :status AND updated_at < :cutoff
            ORDER BY updated_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Order> lockStuck(@Param("status") String status,
                          @Param("cutoff") Instant cutoff,
                          @Param("batchSize") int batchSize);

    interface StatusCount {
        String getStatus();
        Long getTotal();
    }

    @Query(value = "SELECT status AS status, COUNT(*) AS total FROM orders GROUP BY status", nativeQuery = true)
    List<StatusCount> countGroupedByStatus();
}

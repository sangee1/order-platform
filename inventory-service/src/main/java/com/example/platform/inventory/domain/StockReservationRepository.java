package com.example.platform.inventory.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    List<StockReservation> findByOrderId(UUID orderId);

    List<StockReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);
}

package com.example.platform.inventory.application;

import com.example.platform.common.events.OrderCreated;
import com.example.platform.common.events.OrderItemLine;
import com.example.platform.common.events.StockReservationFailed;
import com.example.platform.common.events.StockReserved;
import com.example.platform.common.events.Topics;
import com.example.platform.inventory.domain.InventoryItem;
import com.example.platform.inventory.domain.InventoryItemRepository;
import com.example.platform.inventory.domain.ProcessedMessage;
import com.example.platform.inventory.domain.ProcessedMessageRepository;
import com.example.platform.inventory.domain.StockReservation;
import com.example.platform.inventory.domain.StockReservationRepository;
import com.example.platform.inventory.outbox.OutboxWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction per message: de-dup check, stock mutation, reservation rows, outbox event and
 * the processed-message marker all commit together or not at all. Do not call this directly from
 * the listener; go through {@link ReservationFacade}, which retries optimistic-lock conflicts.
 */
@Service
public class ReservationService {

    private final InventoryItemRepository items;
    private final StockReservationRepository reservations;
    private final ProcessedMessageRepository processed;
    private final OutboxWriter outbox;

    public ReservationService(InventoryItemRepository items, StockReservationRepository reservations,
                       ProcessedMessageRepository processed, OutboxWriter outbox) {
        this.items = items;
        this.reservations = reservations;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public void reserveForOrder(UUID messageId, OrderCreated order) {
        if (processed.existsById(messageId)) {
            return; // duplicate delivery: already handled
        }

        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (OrderItemLine line : order.items()) {
            wanted.merge(line.sku(), line.quantity(), Integer::sum);
        }

        List<InventoryItem> found = items.findAllById(wanted.keySet());
        String failure = findFailure(wanted, found);

        if (failure == null) {
            // All-or-nothing: every line is reserved in this one transaction.
            found.forEach(item -> {
                int qty = wanted.get(item.getSku());
                item.reserve(qty);
                reservations.save(new StockReservation(order.orderId(), item.getSku(), qty));
            });
            items.saveAllAndFlush(found); // flush now so a version conflict surfaces here, not at commit
            outbox.append(Topics.INVENTORY_EVENTS, new StockReserved(order.orderId()));
        } else {
            outbox.append(Topics.INVENTORY_EVENTS, new StockReservationFailed(order.orderId(), failure));
        }

        // Unique PK: if two consumers race on the same message, exactly one commits.
        processed.saveAndFlush(new ProcessedMessage(messageId));
    }

    private static String findFailure(Map<String, Integer> wanted, List<InventoryItem> found) {
        if (found.size() != wanted.size()) {
            return "Unknown SKU in order";
        }
        for (InventoryItem item : found) {
            if (!item.canReserve(wanted.get(item.getSku()))) {
                return "Insufficient stock for " + item.getSku();
            }
        }
        return null;
    }
}

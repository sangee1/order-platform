package com.example.platform.payment.application;

import com.example.platform.common.events.PaymentRequested;
import com.example.platform.payment.domain.ProcessedMessageRepository;
import com.example.platform.payment.gateway.PaymentGateway;
import com.example.platform.payment.gateway.PaymentResult;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Charges first, records second, on purpose: the remote call is NOT made inside a database transaction
 * (holding a connection and locks open across a network call is a classic outage cause). The price is a
 * crash window between "charged" and "recorded"; the gateway idempotency key (order id) closes it, because
 * the redelivered message re-asks the gateway and gets the original answer rather than a second charge.
 */
@Component
public class PaymentHandler {

    private final PaymentGateway gateway;
    private final PaymentService service;
    private final ProcessedMessageRepository processed;

    public PaymentHandler(PaymentGateway gateway, PaymentService service, ProcessedMessageRepository processed) {
        this.gateway = gateway;
        this.service = service;
        this.processed = processed;
    }

    public void handle(UUID messageId, PaymentRequested request) {
        if (processed.existsById(messageId)) {
            return; // cheap early exit for duplicate deliveries
        }
        PaymentResult result = gateway.charge(request.orderId().toString(), request.customerId(), request.amount());
        try {
            service.record(messageId, request, result);
        } catch (DataIntegrityViolationException e) {
            // A concurrent duplicate won the race and already committed: nothing left to do.
            if (!processed.existsById(messageId)) {
                throw e;
            }
        }
    }
}

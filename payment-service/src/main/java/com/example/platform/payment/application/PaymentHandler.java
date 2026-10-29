package com.example.platform.payment.application;

import com.example.platform.common.events.PaymentRequested;
import com.example.platform.payment.domain.ProcessedMessageRepository;
import com.example.platform.payment.gateway.PaymentGateway;
import com.example.platform.payment.gateway.PaymentGatewayUnavailableException;
import com.example.platform.payment.gateway.PaymentResult;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Charges first, records second, on purpose: the remote call is NOT made inside a database transaction
 * (holding a connection and locks open across a network call is a classic outage cause). The price is a
 * crash window between "charged" and "recorded"; the gateway idempotency key (order id) closes it, because
 * a retry re-asks the gateway and gets the original answer rather than a second charge.
 *
 * <p>If the gateway cannot answer at all, the outcome is unknown. We record nothing and do not mark the message
 * processed. The order saga's timeout sweeper re-sends PaymentRequested later and, thanks to the idempotency
 * key, the retry either finds the earlier charge or makes the first one.
 */
@Component
public class PaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);

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

        PaymentResult result;
        try {
            result = gateway.charge(request.orderId().toString(), request.customerId(), request.amount());
        } catch (PaymentGatewayUnavailableException e) {
            log.warn("Payment gateway unavailable for order {} ({}). Outcome unknown, nothing recorded; "
                    + "the order saga will re-request payment.", request.orderId(), e.getMessage());
            return;
        }

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

package com.example.platform.payment.application;

import com.example.platform.common.events.DomainEvent;
import com.example.platform.common.events.PaymentCompleted;
import com.example.platform.common.events.PaymentFailed;
import com.example.platform.common.events.PaymentRequested;
import com.example.platform.common.events.Topics;
import com.example.platform.payment.domain.Payment;
import com.example.platform.payment.domain.PaymentRepository;
import com.example.platform.payment.domain.PaymentStatus;
import com.example.platform.payment.domain.ProcessedMessage;
import com.example.platform.payment.domain.ProcessedMessageRepository;
import com.example.platform.payment.gateway.PaymentResult;
import com.example.platform.payment.outbox.OutboxWriter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The database half of handling a payment request: record the outcome, emit the event, mark the message done. */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final ProcessedMessageRepository processed;
    private final OutboxWriter outbox;

    public PaymentService(PaymentRepository payments, ProcessedMessageRepository processed, OutboxWriter outbox) {
        this.payments = payments;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public void record(UUID messageId, PaymentRequested request, PaymentResult result) {
        if (processed.existsById(messageId)) {
            return;
        }
        // One payment per order. If the saga asks again (new message, same order) we keep the original
        // outcome and simply re-announce it, so the orchestrator always gets an answer.
        Payment payment = payments.findByOrderId(request.orderId()).orElseGet(() -> payments.save(
                result.approved()
                        ? Payment.completed(request.orderId(), request.amount(), result.transactionId())
                        : Payment.failed(request.orderId(), request.amount(), result.declineReason())));

        DomainEvent outcome = payment.getStatus() == PaymentStatus.COMPLETED
                ? new PaymentCompleted(payment.getOrderId(), payment.getTransactionId())
                : new PaymentFailed(payment.getOrderId(), payment.getFailureReason());
        outbox.append(Topics.PAYMENT_EVENTS, outcome);

        processed.saveAndFlush(new ProcessedMessage(messageId));
    }
}

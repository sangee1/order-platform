package com.example.platform.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.common.events.PaymentRequested;
import com.example.platform.payment.domain.Payment;
import com.example.platform.payment.domain.PaymentRepository;
import com.example.platform.payment.domain.PaymentStatus;
import com.example.platform.payment.domain.ProcessedMessageRepository;
import com.example.platform.payment.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.publisher.enabled=false",
        "payment.mock.decline-above=1000.00",
        "payment.mock.failure-rate=0.0"})
@Testcontainers
class PaymentHandlerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PaymentHandler handler;
    @Autowired PaymentRepository payments;
    @Autowired ProcessedMessageRepository processed;
    @Autowired OutboxRepository outbox;

    @BeforeEach
    void clean() {
        outbox.deleteAllInBatch();
        processed.deleteAllInBatch();
        payments.deleteAllInBatch();
    }

    private static PaymentRequested request(String amount) {
        return new PaymentRequested(UUID.randomUUID(), "c-1", new BigDecimal(amount));
    }

    @Test
    void approvedPaymentIsRecordedAndAnnounced() {
        PaymentRequested req = request("50.00");

        handler.handle(UUID.randomUUID(), req);

        Payment p = payments.findByOrderId(req.orderId()).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(p.getTransactionId()).isNotBlank();
        assertThat(outbox.countByEventType("PaymentCompleted")).isEqualTo(1);
    }

    @Test
    void declinedPaymentIsRecordedAndAnnouncedAsFailure() {
        PaymentRequested req = request("5000.00");

        handler.handle(UUID.randomUUID(), req);

        Payment p = payments.findByOrderId(req.orderId()).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getFailureReason()).contains("declined");
        assertThat(outbox.countByEventType("PaymentFailed")).isEqualTo(1);
    }

    @Test
    void redeliveredMessageChargesAndEmitsOnce() {
        UUID messageId = UUID.randomUUID();
        PaymentRequested req = request("50.00");

        handler.handle(messageId, req);
        handler.handle(messageId, req);

        assertThat(payments.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void secondRequestForSameOrderKeepsOriginalPaymentAndReannouncesOutcome() {
        PaymentRequested req = request("50.00");

        handler.handle(UUID.randomUUID(), req);
        handler.handle(UUID.randomUUID(), req);   // new message id, same order

        assertThat(payments.count()).isEqualTo(1);
        assertThat(outbox.countByEventType("PaymentCompleted")).isEqualTo(2);
    }
}

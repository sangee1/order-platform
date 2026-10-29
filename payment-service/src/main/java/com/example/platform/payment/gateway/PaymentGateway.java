package com.example.platform.payment.gateway;

import java.math.BigDecimal;

/**
 * Port to the external payment provider. The idempotency key (we use the order id) must make repeated
 * calls return the original outcome instead of charging twice. Real providers such as Stripe support this.
 */
public interface PaymentGateway {
    PaymentResult charge(String idempotencyKey, String customerId, BigDecimal amount);
}

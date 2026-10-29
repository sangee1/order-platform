package com.example.platform.payment.gateway;

public record PaymentResult(boolean approved, String transactionId, String declineReason) {

    public static PaymentResult approved(String transactionId) {
        return new PaymentResult(true, transactionId, null);
    }

    public static PaymentResult declined(String reason) {
        return new PaymentResult(false, null, reason);
    }
}

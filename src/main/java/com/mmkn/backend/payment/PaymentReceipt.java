package com.mmkn.backend.payment;

import java.time.Instant;

public record PaymentReceipt(String paymentId, String quoteId, String reference, String status,
                             int serviceId, String serviceName, String subscriberNumber,
                             long totalPiastres, String currency, Instant paidAt) {}

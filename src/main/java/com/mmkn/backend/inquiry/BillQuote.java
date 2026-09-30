package com.mmkn.backend.inquiry;

import java.time.Instant;

public record BillQuote(String quoteId, int serviceId, String serviceName,
                        String subscriberNumber, String customerName, String billMonth,
                        String currency, long amountDuePiastres, long serviceFeePiastres,
                        long vatPiastres, long totalPiastres, Instant createdAt, Instant expiresAt) {}

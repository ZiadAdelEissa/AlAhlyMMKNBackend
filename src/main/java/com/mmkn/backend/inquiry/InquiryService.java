package com.mmkn.backend.inquiry;

import com.mmkn.backend.catalog.ServiceCatalog;
import com.mmkn.backend.payment.PaymentService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InquiryService {
    private final ServiceCatalog catalog;
    private final PaymentService payments;

    public InquiryService(ServiceCatalog catalog, PaymentService payments) { this.catalog = catalog; this.payments = payments; }

    public BillQuote inquire(int serviceId, String subscriberNumber) {
        if (serviceId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SERVICE_ID", "Choose a service.");
        }
        if (subscriberNumber == null || !subscriberNumber.matches("[0-9]{10}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBSCRIBER_NUMBER", "Subscriber number must contain exactly 10 digits.");
        }
        var service = catalog.getServices().values().stream().flatMap(java.util.Collection::stream)
                .filter(item -> item.id() == serviceId).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found."));
        if (!service.available()) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_UNAVAILABLE", "This service is temporarily unavailable.");
        }
        if (subscriberNumber.equals("1024750892")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_OUTSTANDING_BILL", "There is no outstanding bill for this subscriber.");
        }
        if (!subscriberNumber.equals("1024750891")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIBER_NOT_FOUND", "Subscriber not found.");
        }
        long amount = switch (serviceId) {
            case 4, 5 -> 12500;
            case 6, 7 -> 8500;
            default -> 24750;
        };
        long fee = 500;
        // VAT POLICY: keep the payable VAT authoritative on the server. The frontend may
        // show an estimate, but must display this returned amount before payment.
        // To remove VAT, change the policy to 0 here and update the UI label, API docs
        // and tests together. Do not accept a client-calculated VAT or total for payment.
        long vat = fee * 14 / 100; // Demo fee is exactly 500 piastres, so no rounding needed.
        Instant now = Instant.now();
        return payments.saveQuote(new BillQuote(UUID.randomUUID().toString(), service.id(), service.name(), subscriberNumber,
                "Mina A.", "2026-08", "EGP", amount, fee, vat, amount + fee + vat, now, now.plusSeconds(300)));
    }
}

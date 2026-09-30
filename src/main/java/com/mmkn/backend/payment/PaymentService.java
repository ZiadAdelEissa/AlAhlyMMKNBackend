package com.mmkn.backend.payment;

import com.mmkn.backend.catalog.ServiceCatalog;
import com.mmkn.backend.inquiry.ApiException;
import com.mmkn.backend.inquiry.BillQuote;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Single-process demo ledger. All state is lost on restart; no real money is moved. */
@Service
public class PaymentService {
    private final ServiceCatalog catalog;
    private final Clock clock;
    private final Map<String, BillQuote> quotes = new HashMap<>();
    private record Attempt(String quoteId, PaymentReceipt receipt) {}
    private final Map<String, Attempt> byKey = new HashMap<>();
    private final Map<String, PaymentReceipt> byBill = new LinkedHashMap<>();

    @Autowired
    public PaymentService(ServiceCatalog catalog) { this(catalog, Clock.systemUTC()); }
    public PaymentService(ServiceCatalog catalog, Clock clock) { this.catalog = catalog; this.clock = clock; }

    private String billKey(BillQuote q) { return q.serviceId() + ":" + q.subscriberNumber() + ":" + q.billMonth(); }

    public synchronized BillQuote saveQuote(BillQuote quote) {
        if (byBill.containsKey(billKey(quote))) {
            throw new ApiException(HttpStatus.CONFLICT, "BILL_ALREADY_PAID", "This bill has already been paid.");
        }
        // Keep a bounded demo store; expired unpaid quotes can be discarded.
        quotes.values().removeIf(q -> !clock.instant().isBefore(q.expiresAt()) && !byBill.containsKey(billKey(q)));
        if (quotes.size() >= 10000) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_CAPACITY_REACHED", "Restart the demo server to reset its data.");
        quotes.put(quote.quoteId(), quote);
        return quote;
    }

    public synchronized PaymentReceipt pay(String quoteId, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,128}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Send an Idempotency-Key of 8 to 128 letters, digits, hyphens or underscores.");
        Attempt replay = byKey.get(key);
        if (replay != null) {
            if (!replay.quoteId().equals(quoteId)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "This key was already used for a different quote.");
            return replay.receipt(); // Retry remains safe even after the quote has expired.
        }
        BillQuote quote = quotes.get(quoteId);
        if (quote == null) throw new ApiException(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", "Quote not found. Check your bill again.");
        PaymentReceipt paid = byBill.get(billKey(quote));
        if (byKey.size() >= 10000) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_CAPACITY_REACHED", "Restart the demo server to reset its data.");
        if (paid != null) {
            byKey.put(key, new Attempt(quoteId, paid));
            return paid; // Another key or quote cannot pay the same bill twice.
        }
        if (!clock.instant().isBefore(quote.expiresAt()))
            throw new ApiException(HttpStatus.CONFLICT, "QUOTE_EXPIRED", "This quote has expired. Check your bill again.");
        boolean available = catalog.getServices().values().stream().flatMap(Collection::stream)
                .anyMatch(s -> s.id() == quote.serviceId() && s.available());
        if (!available) throw new ApiException(HttpStatus.CONFLICT, "SERVICE_UNAVAILABLE", "This service is temporarily unavailable.");
        String id = UUID.randomUUID().toString();
        String reference = "MP-" + LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + id;
        PaymentReceipt receipt = new PaymentReceipt(id, quoteId, reference, "SUCCESS", quote.serviceId(),
                quote.serviceName(), quote.subscriberNumber(), quote.totalPiastres(), quote.currency(), clock.instant());
        byBill.put(billKey(quote), receipt);
        byKey.put(key, new Attempt(quoteId, receipt));
        return receipt;
    }

    public synchronized List<PaymentReceipt> history() {
        var result = new ArrayList<>(byBill.values());
        Collections.reverse(result);
        return List.copyOf(result);
    }
}

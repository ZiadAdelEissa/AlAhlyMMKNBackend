package com.mmkn.backend.payment;

import com.mmkn.backend.inquiry.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PaymentReceipt> pay(@RequestBody JsonNode body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        if (!body.isObject() || body.size() != 1 || !body.has("quoteId") || !body.get("quoteId").isString())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Send only quoteId as a UUID string. Amounts are read from the saved quote.");
        String id = body.get("quoteId").asText();
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "quoteId must be a canonical UUID string."); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pay(id, key));
    }

    @GetMapping
    public ResponseEntity<List<PaymentReceipt>> history() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history());
    }
}

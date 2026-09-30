package com.mmkn.backend.inquiry;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class InquiryController {
    private final InquiryService service;

    public InquiryController(InquiryService service) { this.service = service; }

    @PostMapping("/api/v1/inquiries")
    public ResponseEntity<BillQuote> inquire(@RequestBody JsonNode request) {
        if (!request.isObject() || !request.has("serviceId") || !request.get("serviceId").isIntegralNumber()
                || !request.get("serviceId").canConvertToInt()
                || !request.has("subscriberNumber") || !request.get("subscriberNumber").isString()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "serviceId must be an integer and subscriberNumber must be a string.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.inquire(request.get("serviceId").asInt(), request.get("subscriberNumber").asText()));
    }
}

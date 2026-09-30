package com.mmkn.backend.catalog;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {
    private final ServiceCatalog catalog;

    public ServiceController(ServiceCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Map<String, List<ServiceItem>> getServices() {
        return catalog.getServices();
    }
}

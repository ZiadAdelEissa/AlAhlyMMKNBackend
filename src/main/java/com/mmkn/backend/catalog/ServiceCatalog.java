package com.mmkn.backend.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ServiceCatalog {
    private final Map<String, List<ServiceItem>> services;

    public ServiceCatalog() {
        var catalog = new LinkedHashMap<String, List<ServiceItem>>();
        catalog.put("electricity", List.of(
                new ServiceItem(1, "Cairo Electricity", true, "/icons/electricity.svg"),
                new ServiceItem(2, "Alexandria Electricity", false, "/icons/electricity.svg"),
                new ServiceItem(3, "North Delta Electricity", true, "/icons/electricity.svg")));
        catalog.put("water", List.of(
                new ServiceItem(4, "Greater Cairo Water", true, "/icons/water.svg"),
                new ServiceItem(5, "Alexandria Water", true, "/icons/water.svg")));
        catalog.put("gas", List.of(
                new ServiceItem(6, "Town Gas", true, "/icons/gas.svg"),
                new ServiceItem(7, "Natgas", true, "/icons/gas.svg")));
        services = Collections.unmodifiableMap(catalog);
    }

    public Map<String, List<ServiceItem>> getServices() {
        return services;
    }
}

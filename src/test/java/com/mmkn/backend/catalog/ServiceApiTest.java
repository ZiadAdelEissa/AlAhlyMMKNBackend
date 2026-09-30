package com.mmkn.backend.catalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceApiTest {
    @LocalServerPort
    private int port;

    @Test
    void returnsEnglishDictionaryWithUniqueIdsWithoutAuthentication() throws Exception {
        var request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/services")).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        JsonNode body = new ObjectMapper().readTree(response.body());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("electricity", "water", "gas");
        var ids = new HashSet<Integer>();
        for (String category : body.propertyNames()) {
            assertThat(body.get(category).isArray()).isTrue();
            assertThat(body.get(category).size()).isPositive();
            for (JsonNode service : body.get(category)) {
                assertThat(service.propertyNames()).containsExactlyInAnyOrder("id", "name", "available", "iconUrl");
                assertThat(service.get("id").isIntegralNumber()).isTrue();
                assertThat(service.get("id").asInt()).isPositive();
                assertThat(ids.add(service.get("id").asInt())).isTrue();
                assertThat(service.get("name").asText()).matches("[A-Za-z ]+");
                assertThat(service.get("available").isBoolean()).isTrue();
                assertThat(service.get("iconUrl").asText()).isEqualTo("/icons/" + category + ".svg");
            }
        }
        assertThat(body.get("electricity").get(0).get("name").asText()).isEqualTo("Cairo Electricity");
        assertThat(body.get("electricity").get(1).get("name").asText()).isEqualTo("Alexandria Electricity");
        assertThat(body.get("electricity").get(1).get("available").asBoolean()).isFalse();
        assertThat(ids).hasSize(7);
    }

    @Test
    void catalogIconsArePublicSvgImages() throws Exception {
        var client = HttpClient.newHttpClient();
        for (String category : java.util.List.of("electricity", "water", "gas")) {
            var response = client.send(HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + port + "/icons/" + category + ".svg")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("image/svg+xml");
            assertThat(response.body()).contains("http://www.w3.org/2000/svg", "<path", "viewBox=\"0 0 24 24\"");
        }
    }
}

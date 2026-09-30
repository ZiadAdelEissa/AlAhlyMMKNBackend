package com.mmkn.backend.inquiry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InquiryApiTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/inquiries"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest
    @CsvSource({"1,24750,25320", "4,12500,13070", "6,8500,9070"})
    void computesQuoteOnServer(int id, long amount, long total) throws Exception {
        var response = post("{\"serviceId\":" + id + ",\"subscriberNumber\":\"1024750891\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        var quote = mapper.readTree(response.body());
        assertThat(quote.get("amountDuePiastres").asLong()).isEqualTo(amount);
        assertThat(quote.get("serviceFeePiastres").asLong()).isEqualTo(500);
        assertThat(quote.get("vatPiastres").asLong()).isEqualTo(70);
        assertThat(quote.get("totalPiastres").asLong()).isEqualTo(total);
        assertThat(quote.get("currency").asText()).isEqualTo("EGP");
        assertThat(quote.get("quoteId").asText()).isNotBlank();
        assertThat(Instant.parse(quote.get("expiresAt").asText()))
                .isEqualTo(Instant.parse(quote.get("createdAt").asText()).plusSeconds(300));
    }

    @ParameterizedTest
    @CsvSource({"999,1024750891,404,SERVICE_NOT_FOUND", "2,1024750891,409,SERVICE_UNAVAILABLE",
            "1,1024750892,404,NO_OUTSTANDING_BILL", "1,0000000000,404,SUBSCRIBER_NOT_FOUND",
            "1,123,400,INVALID_SUBSCRIBER_NUMBER", "1,abcdefghij,400,INVALID_SUBSCRIBER_NUMBER"})
    void rejectsInvalidInquiries(int id, String subscriber, int status, String code) throws Exception {
        var response = post("{\"serviceId\":" + id + ",\"subscriberNumber\":\"" + subscriber + "\"}");
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).get("code").asText()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"serviceId\":\"1\",\"subscriberNumber\":\"1024750891\"}", "{\"serviceId\":1.5,\"subscriberNumber\":\"1024750891\"}", "{\"serviceId\":2147483648,\"subscriberNumber\":\"1024750891\"}","{}", "null", "[]", "{", "{\"serviceId\":1,\"subscriberNumber\":1024750891}",
            "{\"serviceId\":null,\"subscriberNumber\":\"1024750891\"}"})
    void rejectsMalformedOrWrongTypeBodies(String body) throws Exception {
        var response = post(body);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void neverUsesClientSuppliedTotal() throws Exception {
        var response = post("{\"serviceId\":1,\"subscriberNumber\":\"1024750891\",\"totalPiastres\":1}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("totalPiastres").asLong()).isEqualTo(25320);
    }
}

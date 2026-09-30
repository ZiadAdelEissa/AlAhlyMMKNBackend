package com.mmkn.backend.payment;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentApiTest {
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newHttpClient();
    final ObjectMapper mapper=new ObjectMapper();
    HttpResponse<String> post(String path,String body,String key)throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(key!=null)b.header("Idempotency-Key",key);
        return client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void paysStoredAmountAndReplaysSafely()throws Exception {
        var inquiry=post("/api/v1/inquiries","{\"serviceId\":7,\"subscriberNumber\":\"1024750891\"}",null);
        assertThat(inquiry.statusCode()).isEqualTo(200);
        String q=mapper.readTree(inquiry.body()).get("quoteId").asText();
        String body="{\"quoteId\":\""+q+"\"}";
        assertThat(post("/api/v1/payments",body,null).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/payments","{\"quoteId\":\""+q+"\",\"totalPiastres\":1}","api-test-1").statusCode()).isEqualTo(400);
        var paid=post("/api/v1/payments",body,"api-test-1");
        assertThat(paid.statusCode()).isEqualTo(200);
        assertThat(paid.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(mapper.readTree(paid.body()).get("totalPiastres").asLong()).isEqualTo(9070);
        assertThat(post("/api/v1/payments",body,"api-test-1").body()).isEqualTo(paid.body());
        assertThat(post("/api/v1/payments",body,"api-test-2").body()).isEqualTo(paid.body());
        assertThat(post("/api/v1/payments","{\"quoteId\":\""+UUID.randomUUID()+"\"}","api-test-1").statusCode()).isEqualTo(409);
        var history=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/payments")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(history.body()).size()).isEqualTo(1);
    }
}

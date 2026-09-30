package com.mmkn.backend.payment;

import com.mmkn.backend.catalog.ServiceCatalog;
import com.mmkn.backend.inquiry.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentServiceTest {
    static final Instant NOW = Instant.parse("2026-09-29T16:00:00Z");
    private PaymentService service() { return new PaymentService(new ServiceCatalog(), Clock.fixed(NOW, ZoneOffset.UTC)); }
    private BillQuote quote(int service, Instant expires) {
        return new BillQuote(UUID.randomUUID().toString(), service, "Demo", "1024750891", "Mina A.", "2026-08", "EGP",24750,500,70,25320,NOW.minusSeconds(10),expires);
    }
    @Test void sameKeyAndDifferentKeysReturnOneReceipt() {
        var s=service(); var q=s.saveQuote(quote(1,NOW.plusSeconds(300)));
        var paid=s.pay(q.quoteId(),"request-0001");
        assertThat(s.pay(q.quoteId(),"request-0001")).isEqualTo(paid);
        assertThat(s.pay(q.quoteId(),"request-0002")).isEqualTo(paid);
        assertThat(s.history()).containsExactly(paid);
        assertThat(paid.totalPiastres()).isEqualTo(25320);
    }
    @Test void differentQuotesForSameBillCannotPayTwice() {
        var s=service(); var a=s.saveQuote(quote(1,NOW.plusSeconds(300))); var b=s.saveQuote(quote(1,NOW.plusSeconds(300)));
        var paid=s.pay(a.quoteId(),"request-0001");
        assertThat(s.pay(b.quoteId(),"request-0002")).isEqualTo(paid);
        assertThat(s.pay(b.quoteId(),"request-0002")).isEqualTo(paid);
        assertThatThrownBy(() -> s.pay(a.quoteId(),"request-0002")).isInstanceOf(ApiException.class).hasMessageContaining("different quote");
        assertThatThrownBy(() -> s.saveQuote(quote(1,NOW.plusSeconds(300)))).isInstanceOf(ApiException.class).hasMessageContaining("already been paid");
        assertThat(s.history()).hasSize(1);
    }
    @Test void expiryAtBoundaryIsRejected() {
        var s=service(); var q=s.saveQuote(quote(1,NOW));
        assertThatThrownBy(() -> s.pay(q.quoteId(),"request-0001")).isInstanceOf(ApiException.class).hasMessageContaining("expired");
        assertThat(s.history()).isEmpty();
    }
    @Test void unavailableUnknownAndMissingKeyAreRejected() {
        var s=service(); var q=s.saveQuote(quote(2,NOW.plusSeconds(300)));
        assertThatThrownBy(() -> s.pay(q.quoteId(),"request-0001")).hasMessageContaining("unavailable");
        assertThatThrownBy(() -> s.pay(UUID.randomUUID().toString(),"request-0001")).hasMessageContaining("not found");
        assertThatThrownBy(() -> s.pay(q.quoteId(),null)).hasMessageContaining("Idempotency-Key");
    }
    @Test void concurrentRequestsProduceOnePayment() throws Exception {
        var s=service(); var q=s.saveQuote(quote(1,NOW.plusSeconds(300)));
        var pool=Executors.newFixedThreadPool(6);
        try {
            var calls=new ArrayList<Callable<PaymentReceipt>>();
            for(int i=0;i<20;i++) { final int n=i; calls.add(() -> s.pay(q.quoteId(),"parallel-"+n)); }
            var receipts=new HashSet<PaymentReceipt>();
            for(var result:pool.invokeAll(calls)) receipts.add(result.get());
            assertThat(receipts).hasSize(1); assertThat(s.history()).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void successfulRetryStillWorksAfterExpiry() {
        class MutableClock extends Clock {
            Instant now=NOW;
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return this;}
            public Instant instant(){return now;}
        }
        var clock=new MutableClock(); var s=new PaymentService(new ServiceCatalog(),clock);
        var q=s.saveQuote(quote(1,NOW.plusSeconds(300))); var paid=s.pay(q.quoteId(),"request-0001");
        clock.now=NOW.plusSeconds(600);
        assertThat(s.pay(q.quoteId(),"request-0001")).isEqualTo(paid);
        assertThat(s.history()).hasSize(1);
    }
}

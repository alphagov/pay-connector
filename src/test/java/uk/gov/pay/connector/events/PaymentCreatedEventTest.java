package uk.gov.pay.connector.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.contains;

public class PaymentCreatedEventTest {

    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2018-03-12T16:25:01.123456Z";

    ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
            .withCreatedDate(ZonedDateTime.of(
                    2018, 
                    3, 
                    12, 
                    16, 
                    25, 
                    1, 
                    123456000, 
                    ZoneId.systemDefault())
            )
            .withExternalId(paymentId)
            .withDescription("new passport")
            .withReference(ServicePaymentReference.of("myref"))
            .withReturnUrl("http://example.com")
            .withAmount(100L)
            .build();
    
    private final PaymentCreatedEvent paymentCreatedEvent = PaymentCreatedEvent.from(chargeEntity);

    @Test
    public void serializesTimeWithMicrosecondPrecision() throws Exception {
        final JsonNode actual = paymentCreatedEvent.toJsonNode();

        assertThat(actual.get("time").textValue(), is(time));
    }

    @Test
    public void serializesPaymentCreatedEventType() {
        final JsonNode actual = paymentCreatedEvent.toJsonNode();

        assertThat(actual.get("event_type").textValue(), is("PaymentCreated"));
    }

    @Test
    public void serializesPaymentResourceType() {
        final JsonNode actual = paymentCreatedEvent.toJsonNode();

        assertThat(actual.get("resource_type").textValue(), is("payment"));
    }

    @Test
    public void serializesPayloadFieldstoJsonNode() {
        final JsonNode actual = paymentCreatedEvent.toJsonNode();

        assertThat(actual.get("payment_id").textValue(), is(paymentId));
        assertThat(actual.get("amount").isValueNode(), is(true));
        assertThat(actual.get("amount").longValue(), is(100L));
        assertThat(actual.get("description").textValue(), is("new passport"));
        assertThat(actual.get("reference").textValue(), is("myref"));
        assertThat(actual.get("return_url").textValue(), is("http://example.com"));
    }

    @Test
    public void serializesPayloadFieldstoJsonString() throws Exception{
        final String actual = paymentCreatedEvent.toJsonString();

        assertThat(actual, containsString("\"payment_id\":\"" + paymentId + "\""));
        assertThat(actual, containsString("\"amount\":100"));
        assertThat(actual, containsString("\"description\":\"new passport\""));
        assertThat(actual, containsString("\"reference\":\"myref\""));
        assertThat(actual, containsString("\"return_url\":\"http://example.com\""));
    }
}

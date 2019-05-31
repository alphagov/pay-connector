package uk.gov.pay.connector.events;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.jackson.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.Test;

import java.io.IOException;
import java.time.ZonedDateTime;

public class PaymentCreatedEventTest {

    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2018-03-12T16:25:01.123456Z";
    
    private final PaymentCreatedEvent paymentCreatedEvent = new PaymentCreatedEvent(
            ZonedDateTime.parse(time),
            paymentId,
            100L,
            "new passport",
            "myref",
            "http://example.com"
    );

    @Test
    public void serializesTimeWithMicrosecondPrecision() throws Exception {
        final JsonNode actual = serializeToJsonNode(paymentCreatedEvent);

        assertThat(actual.get("time").textValue(), is(time));
    }

    @Test
    public void serializesPaymentCreatedEventType() {
        final JsonNode actual = serializeToJsonNode(paymentCreatedEvent);

        assertThat(actual.get("event_type").textValue(), is("PaymentCreated"));
    }

    @Test
    public void serializesPaymentResourceType() {
        final JsonNode actual = serializeToJsonNode(paymentCreatedEvent);

        assertThat(actual.get("resource_type").textValue(), is("payment"));
    }

    @Test
    public void serializesPayloadFields() {
        final JsonNode actual = serializeToJsonNode(paymentCreatedEvent);

        assertThat(actual.get("payment_id").textValue(), is(paymentId));
        assertThat(actual.get("amount").isValueNode(), is(true));
        assertThat(actual.get("amount").longValue(), is(100L));
        assertThat(actual.get("description").textValue(), is("new passport"));
        assertThat(actual.get("reference").textValue(), is("myref"));
        assertThat(actual.get("return_url").textValue(), is("http://example.com"));
    }

    private JsonNode serializeToJsonNode(PaymentCreatedEvent paymentCreatedEvent) {
        try {
            return MAPPER.readValue(
                    MAPPER.writeValueAsString(paymentCreatedEvent),
                    JsonNode.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}


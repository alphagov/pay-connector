package uk.gov.pay.connector.charge.model;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.junit.Test;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.State;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeResponse;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Java6Assertions.assertThat;

public class TelephonePaymentJSONTest {

    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    @Test
    public void correctlyDeserializesFromJSON() throws Exception {
        final Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        final PaymentOutcome paymentOutcome = new PaymentOutcome("success", "P0010", supplemental);
        final TelephoneChargeCreateRequest createTelephonePaymentRequest = new TelephoneChargeCreateRequest.ChargeBuilder()
                .amount(12000L)
                .reference("MRPC12345")
                .description("New passport application")
                .createdDate("2018-02-21T16:04:25Z")
                .authorisedDate("2018-02-21T16:05:33Z")
                .processorId("183f2j8923j8")
                .providerId("17498-8412u9-1273891239")
                .authCode("666")
                .paymentOutcome(paymentOutcome)
                .cardType("master-card")
                .nameOnCard("Jane Doe")
                .emailAddress("jane_doe@example.com")
                .cardExpiry("02/19")
                .lastFourDigits("1234")
                .firstSixDigits("654321")
                .telephoneNumber("+447700900796")
                .build();
        
        TelephoneChargeCreateRequest deserializedCreateTelephonePaymentRequest = MAPPER.readValue(fixture("fixtures/TelephoneChargeCreateRequest.json"), TelephoneChargeCreateRequest.class);
        
        assertThat(createTelephonePaymentRequest).isEqualToComparingFieldByFieldRecursively(deserializedCreateTelephonePaymentRequest);
    }

    @Test
    public void correctlySerializesToJSON() throws Exception {
        final Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        final PaymentOutcome paymentOutcome = new PaymentOutcome("success", "P0010", supplemental);
        final State state = new State("success", true, "created", "P0010");

        final TelephoneChargeResponse createTelephoneChargeResponse = new TelephoneChargeResponse.ChargeBuilder()
                .amount(12000L)
                .reference("MRPC12345")
                .description("New passport application")
                .createdDate("2018-02-21T16:04:25Z")
                .authorisedDate("2018-02-21T16:05:33Z")
                .processorId("183f2j8923j8")
                .providerId("17498-8412u9-1273891239")
                .authCode("666")
                .paymentOutcome(paymentOutcome)
                .cardType("master-card")
                .nameOnCard("Jane Doe")
                .emailAddress("jane_doe@example.com")
                .cardExpiry("02/19")
                .lastFourDigits("1234")
                .firstSixDigits("654321")
                .telephoneNumber("+447700900796")
                .paymentId("hu20sqlact5260q2nanm0q8u93")
                .state(state)
                .build();

        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture("fixtures/TelephoneChargeResponse.json"), TelephoneChargeResponse.class));
        final String actual = MAPPER.writeValueAsString(createTelephoneChargeResponse);
        
        assertThat(actual, is(expected));
    }
}

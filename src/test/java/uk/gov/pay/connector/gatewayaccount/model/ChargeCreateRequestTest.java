package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ChargeCreateRequestTest {

    @Test
    public void toStringWithoutPersonalIdentifiableInformation() {
        ChargeCreateRequest chargeCreateRequest = ChargeCreateRequestBuilder.aChargeCreateRequest()
                .withDescription("Test description")
                .build();

        final String stringRepresentation = chargeCreateRequest.toStringWithoutPersonalIdentifiableInformation();
        assertThat(stringRepresentation.contains("Test description"), is(false));
    }
}

package uk.gov.pay.connector.charge.model;

import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ChargeCreateRequestTest {

    @Test
    public void toStringWithoutDescription() {
        ChargeCreateRequest chargeCreateRequest = ChargeCreateRequestBuilder.aChargeCreateRequest()
                .withDescription("Test description")
                .build();

        final String stringRepresentation = chargeCreateRequest.toStringWithoutPersonalIdentifiableInformation();
        assertThat(stringRepresentation.contains("Test description"), is(false));
    }

    @Test
    public void toStringWithoutEmail() {
        ChargeCreateRequest chargeCreateRequest = ChargeCreateRequestBuilder.aChargeCreateRequest()
                .withEmail("test@example.com")
                .build();

        final String stringRepresentation = chargeCreateRequest.toStringWithoutPersonalIdentifiableInformation();
        assertThat(stringRepresentation.contains("test@example.com"), is(false));
    }
}

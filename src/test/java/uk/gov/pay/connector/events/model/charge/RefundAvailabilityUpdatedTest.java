package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;

import java.time.Instant;
import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

class RefundAvailabilityUpdatedTest {
    private final ChargeEntityFixture chargeEntity = aValidChargeEntity();

    @Test
    void serialisesRefundAvailabilityEventDetails() throws JsonProcessingException {

        ChargeEntity charge = chargeEntity.build();
        
        String event = new RefundAvailabilityUpdated(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                RefundAvailabilityUpdatedEventDetails.from(Charge.from(charge), List.of(), ExternalChargeRefundAvailability.EXTERNAL_FULL),
                Instant.now()
        ).toJsonString();
        
        assertThat(event, hasJsonPath("$.event_type", equalTo("REFUND_AVAILABILITY_UPDATED")));
        assertThat(event, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(event, hasJsonPath("$.resource_external_id", equalTo(charge.getExternalId())));

        assertThat(event, hasJsonPath("$.event_details.refund_status", equalTo("full")));
        assertThat(event, hasJsonPath("$.event_details.refund_amount_available", equalTo(500)));
        assertThat(event, hasJsonPath("$.event_details.refund_amount_refunded", equalTo(0)));
    }
}

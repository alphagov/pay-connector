package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.fee.model.Fee;

import java.time.ZonedDateTime;
import java.util.Optional;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.service.payments.commons.model.CommonDateTimeFormatters.ISO_INSTANT_MICROSECOND_PRECISION;

class CaptureConfirmedTest {

    private final ChargeEntityFixture chargeEntity = aValidChargeEntity().withStatus(CAPTURED);

    @Test
    void serializesEventDetailsGivenChargeEvent() throws JsonProcessingException {
        ZonedDateTime gatewayEventTime = ZonedDateTime.parse("2018-03-12T16:25:01.123456Z");
        ZonedDateTime updated = ZonedDateTime.parse("2018-03-12T16:25:02.123456Z");
        Long fee = 5L;

        chargeEntity.withFee(Fee.of(null, fee));
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.of(gatewayEventTime));
        when(chargeEvent.getUpdated()).thenReturn(updated);

        String actual = CaptureConfirmed.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CAPTURE_CONFIRMED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));

        assertThat(actual, hasJsonPath("$.event_details.gateway_event_date", equalTo("2018-03-12T16:25:01.123456Z")));
        assertThat(actual, hasJsonPath("$.event_details.fee", equalTo(5)));
        assertThat(actual, hasJsonPath("$.event_details.net_amount", equalTo(495)));
        assertThat(actual, hasJsonPath("$.event_details.captured_date", equalTo("2018-03-12T16:25:02.123456Z")));
    }

    @Test
    void shouldSerializeEventDetailsWithoutFeeGivenChargeWithMultipleFees() throws JsonProcessingException {
        ZonedDateTime gatewayEventTime = ZonedDateTime.parse("2018-03-12T16:25:01.123456Z");
        ZonedDateTime updated = ZonedDateTime.parse("2018-03-12T16:25:02.123456Z");
        Long fee = 5L;

        chargeEntity.withFee(Fee.of(FeeType.TRANSACTION, fee));
        chargeEntity.withFee(Fee.of(FeeType.RADAR, 10L));
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.of(gatewayEventTime));
        when(chargeEvent.getUpdated()).thenReturn(updated);

        String actual = CaptureConfirmed.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CAPTURE_CONFIRMED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(ISO_INSTANT_MICROSECOND_PRECISION.format(updated))));

        assertThat(actual, hasJsonPath("$.event_details.gateway_event_date", equalTo("2018-03-12T16:25:01.123456Z")));
        assertThat(actual, hasNoJsonPath("$.event_details.fee"));
        assertThat(actual, hasNoJsonPath("$.event_details.net_amount"));
        assertThat(actual, hasJsonPath("$.event_details.captured_date", equalTo("2018-03-12T16:25:02.123456Z")));
    }

    @Test
    void serializesEventGivenNoDetailValues() throws JsonProcessingException {
        ZonedDateTime updated = ZonedDateTime.parse("2018-03-12T16:25:02.123456Z");
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getUpdated()).thenReturn(updated);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.empty());

        String actual = CaptureConfirmed.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CAPTURE_CONFIRMED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(ISO_INSTANT_MICROSECOND_PRECISION.format(updated))));

        assertThat(actual, hasNoJsonPath("$.event_details.gateway_event_date"));
        assertThat(actual, hasNoJsonPath("$.event_details.fee"));
        assertThat(actual, hasNoJsonPath("$.event_details.net_amount"));
    }

}

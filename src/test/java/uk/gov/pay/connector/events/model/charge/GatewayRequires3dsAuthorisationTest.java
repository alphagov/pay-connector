package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;

import java.time.ZonedDateTime;
import java.util.Optional;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

class GatewayRequires3dsAuthorisationTest {

    private final ChargeEntityFixture chargeEntity = aValidChargeEntity();

    @Test
    void serializesEventDetailsGivenChargeEvent() throws JsonProcessingException {
        ZonedDateTime updated = ZonedDateTime.parse("2018-03-12T16:25:02.123456Z");

        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1.0");
        chargeEntity.withAuth3dsDetailsEntity(auth3dsRequiredEntity);

        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getUpdated()).thenReturn(updated);

        String actual = GatewayRequires3dsAuthorisation.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_REQUIRES_3DS_AUTHORISATION")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo("2018-03-12T16:25:02.123456Z")));


        assertThat(actual, hasJsonPath("$.event_details.requires_3ds", equalTo(true)));
        assertThat(actual, hasJsonPath("$.event_details.version_3ds", equalTo("2.1.0")));
    }

    @Test
    void serializesEventCorrectlyWhenVersion3dsIsNotAvailable() throws JsonProcessingException {
        ZonedDateTime updated = ZonedDateTime.parse("2018-03-12T16:25:02.123456Z");

        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getUpdated()).thenReturn(updated);
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.empty());

        String actual = GatewayRequires3dsAuthorisation.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_REQUIRES_3DS_AUTHORISATION")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEvent.getChargeEntity().getExternalId())));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo("2018-03-12T16:25:02.123456Z")));

        assertThat(actual, hasJsonPath("$.event_details.requires_3ds", equalTo(true)));
        assertThat(actual, hasNoJsonPath("$.event_details.version_3ds"));
    }

}

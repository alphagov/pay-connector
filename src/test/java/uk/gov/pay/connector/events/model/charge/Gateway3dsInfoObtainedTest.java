package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.service.payments.commons.api.json.MicrosecondPrecisionDateTimeSerializer.MICROSECOND_FORMATTER;

class Gateway3dsInfoObtainedTest {

    @Test
    void serializesEventDetailsWithVersion3ds() throws JsonProcessingException {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1.0");
        var chargeEntity = ChargeEntityFixture.aValidChargeEntity().withAuth3dsDetailsEntity(auth3dsRequiredEntity).build();
        var eventDate = Instant.now();
        String actual = Gateway3dsInfoObtained.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_3DS_INFO_OBTAINED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.version_3ds", equalTo(chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getThreeDsVersion())));
    }

    @Test
    void serializesEventDetailsWithVersion3dsNull() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture.aValidChargeEntity().withAuth3dsDetailsEntity(new Auth3dsRequiredEntity()).build();
        var eventDate = Instant.now();
        String actual = Gateway3dsInfoObtained.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_3DS_INFO_OBTAINED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasNoJsonPath("$.event_details.version_3ds"));
    }
}

package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.service.payments.commons.api.json.MicrosecondPrecisionDateTimeSerializer.MICROSECOND_FORMATTER;

class Requested3dsExemptionTest {

    @Test
    void serializesRequest3dsOptimiseExemptionEventDetails() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withExemption3dsType(Exemption3dsType.OPTIMISED)
                .build();
        var eventDate = Instant.now();
        String actual = Requested3dsExemption.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("REQUESTED_3DS_EXEMPTION")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.exemption_3ds_requested", equalTo("OPTIMISED")));
    }

    @Test
    void serializesRequest3dsCorporateExemptionEventDetails() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withExemption3dsType(Exemption3dsType.CORPORATE)
                .build();
        var eventDate = Instant.now();
        String actual = Requested3dsExemption.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("REQUESTED_3DS_EXEMPTION")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.exemption_3ds_requested", equalTo("CORPORATE")));
    }
}
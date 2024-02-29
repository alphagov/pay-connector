package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.service.payments.commons.api.json.MicrosecondPrecisionDateTimeSerializer.MICROSECOND_FORMATTER;

class Gateway3dsExemptionResultObtainedTest {

    @Test
    void serializesEventDetailsWithExemption3dsHonoured() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture.aValidChargeEntity().withExemption3ds(Exemption3ds.EXEMPTION_HONOURED).build();
        var eventDate = Instant.now();
        String actual = Gateway3dsExemptionResultObtained.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_3DS_EXEMPTION_RESULT_OBTAINED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.exemption3ds", equalTo(chargeEntity.getChargeCardDetails().getExemption3ds().toString())));
    }

    @Test
    void serializesEventDetailsWithExemption3dsNull() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture.aValidChargeEntity().withExemption3ds(null).build();
        var eventDate = Instant.now();
        String actual = Gateway3dsExemptionResultObtained.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_3DS_EXEMPTION_RESULT_OBTAINED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasNoJsonPath("$.event_details.exemption3ds"));
    }

}

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
        assertThat(actual, hasJsonPath("$.event_details.exemption3ds", equalTo("EXEMPTION_HONOURED")));
    }

    @Test
    void serializesEventDetailsWithExemption3dsNotRequested() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture.aValidChargeEntity().withExemption3ds(Exemption3ds.EXEMPTION_NOT_REQUESTED).build();
        var eventDate = Instant.now();
        String actual = Gateway3dsExemptionResultObtained.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_3DS_EXEMPTION_RESULT_OBTAINED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.exemption3ds", equalTo("EXEMPTION_NOT_REQUESTED")));
    }

    @Test
    void serializesEventDetailsWithExemption3dsOutOfScope() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture.aValidChargeEntity().withExemption3ds(Exemption3ds.EXEMPTION_OUT_OF_SCOPE).build();
        var eventDate = Instant.now();
        String actual = Gateway3dsExemptionResultObtained.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_3DS_EXEMPTION_RESULT_OBTAINED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.exemption3ds", equalTo("EXEMPTION_OUT_OF_SCOPE")));
    }

    @Test
    void serializesEventDetailsWithExemption3dsRejected() throws JsonProcessingException {
        var chargeEntity = ChargeEntityFixture.aValidChargeEntity().withExemption3ds(Exemption3ds.EXEMPTION_REJECTED).build();
        var eventDate = Instant.now();
        String actual = Gateway3dsExemptionResultObtained.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_3DS_EXEMPTION_RESULT_OBTAINED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.exemption3ds", equalTo("EXEMPTION_REJECTED")));
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

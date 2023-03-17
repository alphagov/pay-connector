package uk.gov.pay.connector.charge.util;

import com.google.common.collect.MapDifference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder.aChargeCreateRequest;

class ChargeCreateRequestIdempotencyComparatorUtilTest {
    private Map<String, Object> idempotencyMap;
    @BeforeEach
    void setUp() {
        idempotencyMap = new HashMap<>();
        idempotencyMap.put("amount", 1500L);
        idempotencyMap.put("description", "a description");
        idempotencyMap.put("reference", "a reference");
        idempotencyMap.put("return_url", null);
        idempotencyMap.put("email", null);
        idempotencyMap.put("delayed_capture", false);
        idempotencyMap.put("language", "ENGLISH");
        idempotencyMap.put("prefilled_cardholder_details", null);
        idempotencyMap.put("metadata", Map.of("key1", "value1"));
        idempotencyMap.put("source", null);
        idempotencyMap.put("moto", false);
        idempotencyMap.put("payment_provider", null);
        idempotencyMap.put("agreement_id", "an agreement");
        idempotencyMap.put("save_payment_instrument_to_agreement", false);
        idempotencyMap.put("authorisation_mode", "agreement");
    }

    @Test
    void shouldReturnNoDifference() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1")))
                .build();

        Map<String, MapDifference.ValueDifference<Object>> diff = ChargeCreateRequestIdempotencyComparatorUtil.diff(chargeCreateRequest, idempotencyMap);
        assertThat(diff.size(), is(0));
    }

    @Test
    void shouldReturnAgreementAndReferenceAsDifferent() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("another reference")
                .withAgreementId("different agreement")
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1")))
                .build();

        Map<String, MapDifference.ValueDifference<Object>> diff = ChargeCreateRequestIdempotencyComparatorUtil.diff(chargeCreateRequest, idempotencyMap);
        assertThat(diff.size(), is(2));
        assertThat(diff.get("agreement_id").leftValue().toString(), is("different agreement"));
        assertThat(diff.get("agreement_id").rightValue().toString(), is("an agreement"));
        assertThat(diff.get("reference").leftValue(), is("another reference"));
        assertThat(diff.get("reference").rightValue(), is("a reference"));
    }

    @Test
    void shouldReturnMetadataAsDifference() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value2")))
                .build();

        Map<String, MapDifference.ValueDifference<Object>> diff = ChargeCreateRequestIdempotencyComparatorUtil.diff(chargeCreateRequest, idempotencyMap);
        assertThat(diff.size(), is(1));
        assertThat(diff.get("metadata").leftValue().toString(), is("{key1=value2}"));
        assertThat(diff.get("metadata").rightValue().toString(), is("{key1=value1}"));
    }
}

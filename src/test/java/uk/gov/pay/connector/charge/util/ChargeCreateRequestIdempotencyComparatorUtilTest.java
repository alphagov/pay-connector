package uk.gov.pay.connector.charge.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
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
        idempotencyMap.put("metadata", Map.of("key1", "value1", "key2", 2023));
        idempotencyMap.put("source", null);
        idempotencyMap.put("moto", false);
        idempotencyMap.put("payment_provider", null);
        idempotencyMap.put("agreement_id", "an agreement");
        idempotencyMap.put("save_payment_instrument_to_agreement", false);
        idempotencyMap.put("authorisation_mode", "web");
    }
    @Test
    void shouldReturnTrue_whenRequestAndIdempotencyMatch() throws Exception {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1", "key2", 2023)))
                .build();

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(true));
    }

    @Test
    void shouldReturnTrue_whenMetadataIsNotPresent() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .build();

        idempotencyMap.put("metadata", null);

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(true));
    }

    @Test
    void shouldReturnFalse_whenIdempotencyMetadataIsNotPresent() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1", "key2", 2023)))
                .build();

        idempotencyMap.put("metadata", null);

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(false));
    }

    @Test
    void shouldReturnFalse_whenAmountIsDifferent() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1600L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1", "key2", 2023)))
                .build();

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(false));
    }

    @Test
    void shouldReturnFalse_whenDescriptionIsDifferent() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("different description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1", "key2", 2023)))
                .build();

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(false));
    }

    @Test
    void shouldReturnFalse_whenReferenceIsDifferent() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("different reference")
                .withAgreementId("an agreement")
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1", "key2", 2023)))
                .build();

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(false));
    }

    @Test
    void shouldReturnFalse_whenAgreementIdIsDifferent() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("different agreement")
                .withExternalMetadata(new ExternalMetadata(
                        Map.of("key1", "value1", "key2", 2023)))
                .build();

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(false));
    }

    @Test
    void shouldReturnFalse_whenChargeRequestMetadataIsNull() {
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(1500L)
                .withDescription("a description")
                .withReference("a reference")
                .withAgreementId("an agreement")
                .build();

        assertThat(ChargeCreateRequestIdempotencyComparatorUtil.compare(chargeCreateRequest, idempotencyMap), is(false));
    }
}

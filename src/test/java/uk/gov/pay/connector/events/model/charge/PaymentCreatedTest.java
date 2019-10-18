package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

@RunWith(JUnitParamsRunner.class)
public class PaymentCreatedTest {

    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2018-03-12T16:25:01.123456Z";

    private final ChargeEntityFixture chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
            .withCreatedDate(ZonedDateTime.parse(time))
            .withExternalId(paymentId)
            .withDescription("new passport")
            .withReference(ServicePaymentReference.of("myref"))
            .withReturnUrl("http://example.com")
            .withAmount(100L)
            .withCorporateSurcharge(77L)
            .withEmail(null)
            .withExternalMetadata(new ExternalMetadata(ImmutableMap.of("key1", "value1", "key2", "value2")));
    private ChargeEntity chargeEntity;

    private String preparePaymentCreatedEvent() throws JsonProcessingException {
        chargeEntity = chargeEntityFixture.build();
        var paymentCreatedEvent = PaymentCreated.from(chargeEntity);
        return paymentCreatedEvent.toJsonString();
    }

    @Test
    public void serializesPayloadForCreatedWithoutCardDetails() throws JsonProcessingException {
        var paymentCreatedEvent = preparePaymentCreatedEvent();

        assertBasePaymentCreatedDetails(paymentCreatedEvent);
        assertDoesNotContainCardDetails(paymentCreatedEvent);
        assertThat(paymentCreatedEvent, hasNoJsonPath("$.event_details.email"));
    }

    @Test
    public void serializesPayloadForCreatedWithCardDetailsAndEmail() throws JsonProcessingException {
        chargeEntityFixture
                .withEmail("test@email.gov.uk")
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity());

        var paymentCreatedEvent = preparePaymentCreatedEvent();

        assertBasePaymentCreatedDetails(paymentCreatedEvent);
        assertContainsCardDetails(paymentCreatedEvent);
        assertThat(paymentCreatedEvent, hasJsonPath("$.event_details.email", equalTo("test@email.gov.uk")));
    }

    @Test
    public void serializesPayloadForNonCreatedWithCardDetails() throws JsonProcessingException {
        chargeEntityFixture
                .withStatus(ChargeStatus.SYSTEM_CANCELLED)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .withEvents(List.of(
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.CREATED, Optional.of(ZonedDateTime.now().minusHours(3)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.SYSTEM_CANCELLED, Optional.of(ZonedDateTime.now().minusHours(3)), Optional.empty())
                ));

        var paymentCreatedEvent = preparePaymentCreatedEvent();

        assertBasePaymentCreatedDetails(paymentCreatedEvent);
        assertContainsCardDetails(paymentCreatedEvent);
        assertThat(paymentCreatedEvent, hasNoJsonPath("$.event_details.email"));
    }

    @Test
    @Parameters(method = "eventStatuses")
    public void serializesPayloadForNonCreatedWithoutCardDetailsWhenContainsChargeEvent(ChargeStatus status) throws JsonProcessingException {
        chargeEntityFixture
                .withStatus(ChargeStatus.SYSTEM_CANCELLED)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .withEvents(List.of(
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.CREATED, Optional.of(ZonedDateTime.now().minusHours(3)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), status, Optional.of(ZonedDateTime.now().minusHours(3)), Optional.empty())
                ));

        var paymentCreatedEvent = preparePaymentCreatedEvent();

        assertBasePaymentCreatedDetails(paymentCreatedEvent);
        assertDoesNotContainCardDetails(paymentCreatedEvent);
        assertThat(paymentCreatedEvent, hasNoJsonPath("$.event_details.email"));
    }

    private Object[] eventStatuses() {
        return new Object[]{
                new Object[]{ChargeStatus.AUTHORISATION_ABORTED},
                new Object[]{ChargeStatus.AUTHORISATION_SUCCESS},
                new Object[]{ChargeStatus.AUTHORISATION_REJECTED},
                new Object[]{ChargeStatus.AUTHORISATION_ERROR},
                new Object[]{ChargeStatus.AUTHORISATION_TIMEOUT},
                new Object[]{ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR},
                new Object[]{ChargeStatus.AUTHORISATION_3DS_REQUIRED},
                new Object[]{ChargeStatus.AUTHORISATION_CANCELLED},
                new Object[]{ChargeStatus.AUTHORISATION_SUBMITTED}
        };
    }

    private void assertDoesNotContainCardDetails(String actual) {
        assertThat(actual, hasNoJsonPath("$.event_details.cardholder_name"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_line_1"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_line_2"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_postcode"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_city"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_county"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_country"));
    }

    private void assertContainsCardDetails(String actual) {
        assertThat(actual, hasJsonPath("$.event_details.cardholder_name", equalTo("Mr Test")));
        assertThat(actual, hasJsonPath("$.event_details.address_line1", equalTo("125 Kingsway")));
        assertThat(actual, hasJsonPath("$.event_details.address_line2", equalTo("Aviation House")));
        assertThat(actual, hasJsonPath("$.event_details.address_postcode", equalTo("WC2B 6NH")));
        assertThat(actual, hasJsonPath("$.event_details.address_city", equalTo("London")));
        assertThat(actual, hasJsonPath("$.event_details.address_county", equalTo("London")));
        assertThat(actual, hasJsonPath("$.event_details.address_country", equalTo("GB")));
    }

    private void assertBasePaymentCreatedDetails(String actual) {
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_CREATED")));
        assertThat(actual, hasJsonPath("$.event_details.amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.description", equalTo("new passport")));
        assertThat(actual, hasJsonPath("$.event_details.reference", equalTo("myref")));
        assertThat(actual, hasJsonPath("$.event_details.live", equalTo(false)));
        assertThat(actual, hasJsonPath("$.event_details.return_url", equalTo("http://example.com")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_account_id", equalTo(chargeEntity.getGatewayAccount().getId().toString())));
        assertThat(actual, hasJsonPath("$.event_details.payment_provider", equalTo(chargeEntity.getGatewayAccount().getGatewayName())));
        assertThat(actual, hasJsonPath("$.event_details.language", equalTo(chargeEntity.getLanguage().toString())));
        assertThat(actual, hasJsonPath("$.event_details.delayed_capture", equalTo(chargeEntity.isDelayedCapture())));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.key1", equalTo("value1")));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.key2", equalTo("value2")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
    }
}

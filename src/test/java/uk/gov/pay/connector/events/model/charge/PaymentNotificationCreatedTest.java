package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.paymentprocessor.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MICROSECOND_PRECISION;
import static uk.gov.service.payments.commons.model.AuthorisationMode.EXTERNAL;
import static uk.gov.service.payments.commons.model.Source.CARD_EXTERNAL_TELEPHONE;

public class PaymentNotificationCreatedTest {

    private final String providerId = "validTransactionId";
    private final ZonedDateTime notificationTimestamp = ZonedDateTime.parse("2022-10-06T15:00:00.123456Z");

    private ChargeEntityFixture chargeEntityFixture;

    @BeforeEach
    void setUp() {
        String paymentId = "jweojfewjoifewj";
        String time = "2018-03-12T16:25:01.123456Z";
        chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse(time))
                .withStatus(ChargeStatus.PAYMENT_NOTIFICATION_CREATED)
                .withExternalId(paymentId)
                .withTransactionId(providerId)
                .withSource(CARD_EXTERNAL_TELEPHONE)
                .withCardDetails(anAuthCardDetails().withAddress(null).getCardDetailsEntity())
                .withAuthorisationMode(EXTERNAL)
                .withAmount(100L);
    }

    @Test
    void whenAllTheDataIsAvailable() throws JsonProcessingException {
        ExternalMetadata externalMetadata = new ExternalMetadata(Map.of(
                "processor_id", "processorID",
                "auth_code", "012345",
                "telephone_number", "+447700900796"));
        chargeEntityFixture
                .withExternalMetadata(externalMetadata)
        .withCardLabelEntity("Visa", "visa");

        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity);
        when(chargeEvent.getUpdated()).thenReturn(notificationTimestamp);

        String actual = PaymentNotificationCreated.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_NOTIFICATION_CREATED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(ISO_INSTANT_MICROSECOND_PRECISION.format(notificationTimestamp))));

        assertThat(actual, hasJsonPath("$.event_details.amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.live", equalTo(false)));
        assertThat(actual, hasJsonPath("$.event_details.description", equalTo("This is a description")));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand", equalTo("visa")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand_label", equalTo("Visa")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(providerId)));
        assertThat(actual, hasJsonPath("$.event_details.first_digits_card_number", equalTo("424242")));
        assertThat(actual, hasJsonPath("$.event_details.last_digits_card_number", equalTo("4242")));
        assertThat(actual, hasJsonPath("$.event_details.cardholder_name", equalTo("Mr Test")));
        assertThat(actual, hasJsonPath("$.event_details.expiry_date", equalTo("12/99")));
        assertThat(actual, hasJsonPath("$.event_details.payment_provider", equalTo("sandbox")));
        assertThat(actual, hasJsonPath("$.event_details.source", equalTo("CARD_EXTERNAL_TELEPHONE")));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.processor_id", equalTo("processorID")));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.auth_code", equalTo("012345")));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.telephone_number", equalTo("+447700900796")));
        assertThat(actual, hasJsonPath("$.event_details.credential_external_id", equalTo(chargeEntity.getGatewayAccountCredentialsEntity().getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.gateway_account_id", equalTo(chargeEntity.getGatewayAccount().getId().intValue())));
        assertThat(actual, hasJsonPath("$.event_details.authorisation_mode", equalTo(chargeEntity.getAuthorisationMode().getName())));
    }

    @Test
    void whenCardDetailsAndMetadataAreNotAvailable() throws JsonProcessingException {
        chargeEntityFixture
                .withCardDetails(null)
                .withExternalMetadata(null);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity);
        when(chargeEvent.getUpdated()).thenReturn(notificationTimestamp);

        String actual = PaymentNotificationCreated.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_NOTIFICATION_CREATED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(ISO_INSTANT_MICROSECOND_PRECISION.format(notificationTimestamp))));
        assertThat(actual, hasJsonPath("$.event_details.live", equalTo(false)));

        assertThat(actual, hasJsonPath("$.event_details.amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.description", equalTo("This is a description")));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(providerId)));
        assertThat(actual, hasJsonPath("$.event_details.payment_provider", equalTo("sandbox")));
        assertThat(actual, hasJsonPath("$.event_details.source", equalTo("CARD_EXTERNAL_TELEPHONE")));

        assertThat(actual, hasNoJsonPath("$.event_details.card_brand"));
        assertThat(actual, hasNoJsonPath("$.event_details.first_digits_card_number"));
        assertThat(actual, hasNoJsonPath("$.event_details.last_digits_card_number"));
        assertThat(actual, hasNoJsonPath("$.event_details.cardholder_name"));
        assertThat(actual, hasNoJsonPath("$.event_details.expiry_date"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.processor_id"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.auth_code"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.telephone_number"));
    }

    @Test
    void whenCardDetailsIsAvailableButNotAllItsFieldsAre() throws JsonProcessingException {
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity();
        cardDetailsEntity.setCardHolderName("Mr Test");

        chargeEntityFixture
                .withCardDetails(cardDetailsEntity)
                .withGatewayAccountCredentialsEntity(null)
                .withExternalMetadata(null);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity);
        when(chargeEvent.getUpdated()).thenReturn(notificationTimestamp);

        String actual = PaymentNotificationCreated.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_NOTIFICATION_CREATED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(ISO_INSTANT_MICROSECOND_PRECISION.format(notificationTimestamp))));
        assertThat(actual, hasJsonPath("$.event_details.live", equalTo(false)));

        assertThat(actual, hasJsonPath("$.event_details.amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.description", equalTo("This is a description")));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(providerId)));
        assertThat(actual, hasJsonPath("$.event_details.payment_provider", equalTo("sandbox")));
        assertThat(actual, hasJsonPath("$.event_details.source", equalTo("CARD_EXTERNAL_TELEPHONE")));
        assertThat(actual, hasJsonPath("$.event_details.cardholder_name", equalTo("Mr Test")));

        assertThat(actual, hasNoJsonPath("$.event_details.card_brand"));
        assertThat(actual, hasNoJsonPath("$.event_details.first_digits_card_number"));
        assertThat(actual, hasNoJsonPath("$.event_details.last_digits_card_number"));
        assertThat(actual, hasNoJsonPath("$.event_details.expiry_date"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.processor_id"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.auth_code"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.telephone_number"));
        assertThat(actual, hasNoJsonPath("$.event_details.credential_external_id"));
    }
}

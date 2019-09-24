package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.Map;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

public class PaymentNotificationCreatedTest {

    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2018-03-12T16:25:01.123456Z";
    private final String providerId = "validTransactionId";

    private ChargeEntityFixture chargeEntityFixture;

    @Before
    public void setUp() throws Exception {
        chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.parse(time))
                .withStatus(ChargeStatus.PAYMENT_NOTIFICATION_CREATED)
                .withExternalId(paymentId)
                .withTransactionId(providerId)
                .withCardDetails(anAuthCardDetails().withAddress(null).getCardDetailsEntity())
                .withAmount(100L);
    }

    @Test
    public void whenAllTheDataIsAvailable() throws JsonProcessingException {
        ExternalMetadata externalMetadata = new ExternalMetadata(Map.of(
                "processor_id", "processorID",
                "auth_code", "012345",
                "telephone_number", "+447700900796"));
        chargeEntityFixture.withExternalMetadata(externalMetadata);

        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity);

        String actual = PaymentNotificationCreated.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_NOTIFICATION_CREATED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));

        assertThat(actual, hasJsonPath("$.event_details.amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.description", equalTo("This is a description")));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand", equalTo("visa")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(providerId)));
        assertThat(actual, hasJsonPath("$.event_details.first_digits_card_number", equalTo("424242")));
        assertThat(actual, hasJsonPath("$.event_details.last_digits_card_number", equalTo("4242")));
        assertThat(actual, hasJsonPath("$.event_details.cardholder_name", equalTo("Mr Test")));
        assertThat(actual, hasJsonPath("$.event_details.card_expiry_date", equalTo("12/99")));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.processor_id", equalTo("processorID")));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.auth_code", equalTo("012345")));
        assertThat(actual, hasJsonPath("$.event_details.external_metadata.telephone_number", equalTo("+447700900796")));
    }

    @Test
    public void whenNotAllTheDataIsAvailable() throws JsonProcessingException {
        chargeEntityFixture
                .withCardDetails(null)
                .withExternalMetadata(null);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity);

        String actual = PaymentNotificationCreated.from(chargeEvent).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_NOTIFICATION_CREATED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));

        assertThat(actual, hasJsonPath("$.event_details.amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.description", equalTo("This is a description")));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(providerId)));

        assertThat(actual, hasNoJsonPath("$.event_details.card_brand"));
        assertThat(actual, hasNoJsonPath("$.event_details.first_digits_card_number"));
        assertThat(actual, hasNoJsonPath("$.event_details.last_digits_card_number"));
        assertThat(actual, hasNoJsonPath("$.event_details.cardholder_name"));
        assertThat(actual, hasNoJsonPath("$.event_details.card_expiry_date"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.processor_id"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.auth_code"));
        assertThat(actual, hasNoJsonPath("$.event_details.external_metadata.telephone_number"));
    }
}
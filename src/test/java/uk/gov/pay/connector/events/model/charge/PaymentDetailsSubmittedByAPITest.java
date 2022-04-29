package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class PaymentDetailsSubmittedByAPITest {

    private final String time = "2018-03-12T16:25:01.123456Z";
    private final String validTransactionId = "validTransactionId";

    private ChargeEntityFixture chargeEntityFixture;

    @BeforeEach
    public void setUp() {
        ZonedDateTime latestDateTime = ZonedDateTime.parse(time);

        List<ChargeEventEntity> list = List.of(
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.CREATED).withUpdated(latestDateTime.minusHours(3)).build(),
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_SUCCESS).withUpdated(latestDateTime).build()
        );

        String paymentId = "jweojfewjoifewj";
        chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse(time))
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withExternalId(paymentId)
                .withTransactionId(validTransactionId)
                .withCorporateSurcharge(10L)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .withAmount(100L)
                .withEvents(list);
    }

    @Test
    public void shouldIncludeAllFieldsForTheEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        String json = PaymentDetailsSubmittedByAPI.from(chargeEntity).toJsonString();

        assertDetails(json, chargeEntity);
    }

    @Test
    public void shouldIncludeAllFieldsForEventEmittedUsingChargeEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        ChargeEventEntity chargeEventEntity = aChargeEventEntity()
                .withUpdated(ZonedDateTime.parse(time))
                .withChargeEntity(chargeEntity)
                .build();
        String json = PaymentDetailsSubmittedByAPI.from(chargeEventEntity).toJsonString();

        assertDetails(json, chargeEntity);
    }

    private void assertDetails(String actual, ChargeEntity chargeEntity) {
        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_DETAILS_SUBMITTED_BY_API")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.title", equalTo("Payment details submitted by API")));
        assertThat(actual, hasJsonPath("$.description", equalTo("The event happens when the payment details are submitted by API")));
        assertThat(actual, hasJsonPath("$.event_details.card_type", equalTo("DEBIT")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand", equalTo("visa")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand_label", equalTo("Visa")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(validTransactionId)));
        assertThat(actual, hasJsonPath("$.event_details.first_digits_card_number", equalTo("424242")));
        assertThat(actual, hasJsonPath("$.event_details.last_digits_card_number", equalTo("4242")));
        assertThat(actual, hasJsonPath("$.event_details.cardholder_name", equalTo("Mr Test")));
        assertThat(actual, hasJsonPath("$.event_details.expiry_date", equalTo("12/99")));
    }
}

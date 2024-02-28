package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.northamericaregion.UsState;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class PaymentDetailsEnteredTest {

    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2018-03-12T16:25:01.123456Z";
    private final String validTransactionId = "validTransactionId";

    private ChargeEntityFixture chargeEntityFixture;

    @BeforeEach
    void setUp() {
        ZonedDateTime latestDateTime = ZonedDateTime.parse(time);

        List<ChargeEventEntity> list = List.of(
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.CREATED).withUpdated(latestDateTime.minusHours(3)).build(),
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_SUCCESS).withUpdated(latestDateTime).build(),
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.ENTERING_CARD_DETAILS).withUpdated(latestDateTime.minusHours(1)).build()
        );

        chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse(time))
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withExternalId(paymentId)
                .withTransactionId(validTransactionId)
                .withCorporateSurcharge(10L)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .withAmount(100L)
                .withEvents(list)
                .withWalletType(WalletType.APPLE_PAY);
    }

    @Test
    void whenAllTheDataIsAvailable() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        String actual = PaymentDetailsEntered.from(chargeEntity).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_DETAILS_ENTERED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.title", equalTo("Payment details entered event")));
        assertThat(actual, hasJsonPath("$.description", equalTo("The event happens when the payment details are entered")));
        assertThat(actual, hasJsonPath("$.event_details.corporate_surcharge", equalTo(10)));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
        assertThat(actual, hasJsonPath("$.event_details.total_amount", equalTo(110)));
        assertThat(actual, hasJsonPath("$.event_details.card_type", equalTo("DEBIT")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand", equalTo("visa")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand_label", equalTo("Visa")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(validTransactionId)));
        assertThat(actual, hasJsonPath("$.event_details.first_digits_card_number", equalTo("424242")));
        assertThat(actual, hasJsonPath("$.event_details.last_digits_card_number", equalTo("4242")));
        assertThat(actual, hasJsonPath("$.event_details.cardholder_name", equalTo("Mr Test")));
        assertThat(actual, hasJsonPath("$.event_details.expiry_date", equalTo("12/99")));
        assertThat(actual, hasJsonPath("$.event_details.address_line1", equalTo("125 Kingsway")));
        assertThat(actual, hasJsonPath("$.event_details.address_line2", equalTo("Aviation House")));
        assertThat(actual, hasJsonPath("$.event_details.address_postcode", equalTo("WC2B 6NH")));
        assertThat(actual, hasJsonPath("$.event_details.address_city", equalTo("London")));
        assertThat(actual, hasJsonPath("$.event_details.address_county", equalTo("London")));
        assertThat(actual, hasJsonPath("$.event_details.address_country", equalTo("GB")));
        assertThat(actual, hasJsonPath("$.event_details.wallet", equalTo("APPLE_PAY")));
    }

    @Test
    void whenAllTheDataIsAvailableForUsCountry() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        chargeEntity.getChargeCardDetails().getBillingAddress().ifPresent(address -> address.setStateOrProvince(UsState.VERMONT.getAbbreviation()));
        String actual = PaymentDetailsEntered.from(chargeEntity).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_DETAILS_ENTERED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.title", equalTo("Payment details entered event")));
        assertThat(actual, hasJsonPath("$.description", equalTo("The event happens when the payment details are entered")));
        assertThat(actual, hasJsonPath("$.event_details.address_state_province", equalTo(UsState.VERMONT.getAbbreviation())));
    }

    @Test
    void whenNotAllTheDataIsAvailable() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withCardDetails(anAuthCardDetails().withAddress(null).withCardNo("4242").withCardType(PayersCardType.CREDIT_OR_DEBIT).getCardDetailsEntity())
                .withWalletType(null)
                .withCorporateSurcharge(null)
                .build();

        String actual = PaymentDetailsEntered.from(chargeEntity).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_DETAILS_ENTERED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.title", equalTo("Payment details entered event")));
        assertThat(actual, hasJsonPath("$.description", equalTo("The event happens when the payment details are entered")));
        assertThat(actual, hasNoJsonPath("$.event_details.corporate_surcharge"));
        assertThat(actual, hasNoJsonPath("$.event_details.total_amount"));
        assertThat(actual, hasNoJsonPath("$.event_details.card_type"));
        assertThat(actual, hasJsonPath("$.event_details.card_brand", equalTo("visa")));
        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo(validTransactionId)));
        assertThat(actual, hasNoJsonPath("$.event_details.first_digits_card_number"));
        assertThat(actual, hasJsonPath("$.event_details.last_digits_card_number", equalTo("4242")));
        assertThat(actual, hasJsonPath("$.event_details.cardholder_name", equalTo("Mr Test")));
        assertThat(actual, hasJsonPath("$.event_details.expiry_date", equalTo("12/99")));
        assertThat(actual, hasNoJsonPath("$.event_details.address_line1"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_line2"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_postcode"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_city"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_county"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_country"));
        assertThat(actual, hasNoJsonPath("$.event_details.address_state_province"));
        assertThat(actual, hasNoJsonPath("$.event_details.wallet"));
    }
}

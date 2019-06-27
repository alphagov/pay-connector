package uk.gov.pay.connector.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PaymentDetailsEnteredEventTest {

    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2018-03-12T16:25:01.123456Z";
    private final String validTransactionId = "validTransactionId";

    private ChargeEntityFixture chargeEntityFixture;

    @Before
    public void setUp() {
        ZonedDateTime latestDateTime = ZonedDateTime.parse(time);

        ChargeEventEntity mockAuthorizationSuccessEvent = mock(ChargeEventEntity.class);
        when(mockAuthorizationSuccessEvent.getUpdated()).thenReturn(latestDateTime);
        when(mockAuthorizationSuccessEvent.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS);

        List<ChargeEventEntity> list = List.of(
                new ChargeEventEntity(new ChargeEntity(), ChargeStatus.CREATED, Optional.empty()),
                mockAuthorizationSuccessEvent,
                new ChargeEventEntity(new ChargeEntity(), ChargeStatus.ENTERING_CARD_DETAILS, Optional.empty())
        );

        chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.parse(time))
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withExternalId(paymentId)
                .withTransactionId(validTransactionId)
                .withCorporateSurcharge(10L)
                .withCardDetails(AuthCardDetailsFixture.anAuthCardDetails().getCardDetailsEntity())
                .withAmount(100L)
                .withEvents(list)
                .withWalletType(WalletType.APPLE_PAY);
    }

    @Test
    public void whenAllTheDataIsAvailable() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        String actual = PaymentDetailsEnteredEvent.from(chargeEntity).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_DETAILS_EVENT")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.title", equalTo("Payment details entered event")));
        assertThat(actual, hasJsonPath("$.description", equalTo("The event happens when the payment details are entered")));

        assertThat(actual, hasJsonPath("$.event_details.corporate_surcharge", equalTo(10)));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
        assertThat(actual, hasJsonPath("$.event_details.card_brand", equalTo("visa")));
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
    public void whenNotAllTheDataIsAvailable() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withCardDetails(AuthCardDetailsFixture.anAuthCardDetails().withAddress(null).withCardNo("4242").getCardDetailsEntity())
                .withWalletType(null)
                .withCorporateSurcharge(null)
                .build();

        String actual = PaymentDetailsEnteredEvent.from(chargeEntity).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("PAYMENT_DETAILS_EVENT")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.title", equalTo("Payment details entered event")));
        assertThat(actual, hasJsonPath("$.description", equalTo("The event happens when the payment details are entered")));

        assertThat(actual, hasNoJsonPath("$.event_details.corporate_surcharge"));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo("test@email.invalid")));
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
        assertThat(actual, hasNoJsonPath("$.event_details.wallet"));
    }
}

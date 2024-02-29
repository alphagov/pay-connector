package uk.gov.pay.connector.charge.util;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.paymentprocessor.util.PaymentInstrumentEntityToAuthCardDetailsConverter;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.paymentprocessor.model.AddressEntity;
import uk.gov.pay.connector.paymentprocessor.model.CardDetailsEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class PaymentInstrumentEntityToAuthCardDetailsConverterTest {

    private final PaymentInstrumentEntityToAuthCardDetailsConverter converter = new PaymentInstrumentEntityToAuthCardDetailsConverter();

    @Test
    void shouldReturnAuthCardDetailsWithCardAndAddressDetail() {
        CardDetailsEntity cardDetailsEntity = anAuthCardDetails()
                .getCardDetailsEntity();
        cardDetailsEntity.setCardType(CardType.DEBIT);
        PaymentInstrumentEntity paymentInstrumentEntity = PaymentInstrumentEntity.PaymentInstrumentEntityBuilder
                .aPaymentInstrumentEntity(Instant.parse("2022-07-11T17:45:40Z"))
                .withCardDetails(cardDetailsEntity).build();

        AuthCardDetails authCardDetails = converter.convert(paymentInstrumentEntity);

        assertAuthCardDetails(authCardDetails, paymentInstrumentEntity, PayersCardType.DEBIT);
        assertThat(authCardDetails.getAddress().isPresent(), is(true));

        Address address = authCardDetails.getAddress().get();
        AddressEntity addressEntity = paymentInstrumentEntity.getCardDetails().getBillingAddress().get();

        assertThat(address.getLine1(), is(addressEntity.getLine1()));
        assertThat(address.getLine2(), is(addressEntity.getLine2()));
        assertThat(address.getCity(), is(addressEntity.getCity()));
        assertThat(address.getCounty(), is(addressEntity.getCounty()));
        assertThat(address.getPostcode(), is(addressEntity.getPostcode()));
        assertThat(address.getCountry(), is(addressEntity.getCountry()));
    }

    @Test
    void shouldReturnAuthCardDetailsWhenCardTypeIsCredit() {
        CardDetailsEntity cardDetailsEntity = anAuthCardDetails().getCardDetailsEntity();
        cardDetailsEntity.setCardType(CardType.CREDIT);

        PaymentInstrumentEntity paymentInstrumentEntity = PaymentInstrumentEntity.PaymentInstrumentEntityBuilder
                .aPaymentInstrumentEntity(Instant.parse("2022-07-11T17:45:40Z"))
                .withCardDetails(cardDetailsEntity).build();

        AuthCardDetails authCardDetails = converter.convert(paymentInstrumentEntity);

        assertAuthCardDetails(authCardDetails, paymentInstrumentEntity, PayersCardType.CREDIT);
    }
    
    @Test
    void shouldReturnAuthCardDetailsWhenCardTypeIsNull() {
        CardDetailsEntity cardDetailsEntity = anAuthCardDetails().getCardDetailsEntity();
        cardDetailsEntity.setCardType(null);
        
        PaymentInstrumentEntity paymentInstrumentEntity = PaymentInstrumentEntity.PaymentInstrumentEntityBuilder
                .aPaymentInstrumentEntity(Instant.parse("2022-07-11T17:45:40Z"))
                .withCardDetails(cardDetailsEntity).build();

        AuthCardDetails authCardDetails = converter.convert(paymentInstrumentEntity);

        assertAuthCardDetails(authCardDetails, paymentInstrumentEntity, PayersCardType.CREDIT_OR_DEBIT);
    }

    @Test
    void shouldReturnAuthCardDetailsWithoutAddress() {
        CardDetailsEntity cardDetailsEntity = anAuthCardDetails()
                .withAddress(null)
                .getCardDetailsEntity();
        cardDetailsEntity.setCardType(CardType.DEBIT);
        PaymentInstrumentEntity paymentInstrumentEntity = PaymentInstrumentEntity.PaymentInstrumentEntityBuilder
                .aPaymentInstrumentEntity(Instant.parse("2022-07-11T17:45:40Z"))
                .withCardDetails(cardDetailsEntity).build();

        AuthCardDetails authCardDetails = converter.convert(paymentInstrumentEntity);

        assertAuthCardDetails(authCardDetails, paymentInstrumentEntity, PayersCardType.DEBIT);
        assertThat(authCardDetails.getAddress().isPresent(), is(false));
    }

    private void assertAuthCardDetails(AuthCardDetails authCardDetails, PaymentInstrumentEntity paymentInstrumentEntity, PayersCardType expectedCardType) {
        assertThat(authCardDetails.getCardHolder(), is("Mr Test"));
        assertThat(authCardDetails.getEndDate().toString(), is("12/99"));

        assertThat(authCardDetails.getCardBrand(), is(paymentInstrumentEntity.getCardDetails().getCardBrand()));
        assertThat(authCardDetails.getPayersCardType(), is(expectedCardType));
    }

}

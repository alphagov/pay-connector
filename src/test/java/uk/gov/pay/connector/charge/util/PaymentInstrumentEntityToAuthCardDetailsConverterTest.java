package uk.gov.pay.connector.charge.util;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.AddressEntity;
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
        PaymentInstrumentEntity paymentInstrumentEntity = PaymentInstrumentEntity.PaymentInstrumentEntityBuilder
                .aPaymentInstrumentEntity(Instant.parse("2022-07-11T17:45:40Z"))
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity()).build();

        AuthCardDetails authCardDetails = converter.convert(paymentInstrumentEntity);

        assertAuthCardDetails(authCardDetails, paymentInstrumentEntity);
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
    void shouldReturnAuthCardDetailsWithoutAddress() {
        PaymentInstrumentEntity paymentInstrumentEntity = PaymentInstrumentEntity.PaymentInstrumentEntityBuilder
                .aPaymentInstrumentEntity(Instant.parse("2022-07-11T17:45:40Z"))
                .withCardDetails(anAuthCardDetails().withAddress(null).getCardDetailsEntity()).build();

        AuthCardDetails authCardDetails = converter.convert(paymentInstrumentEntity);

        assertAuthCardDetails(authCardDetails, paymentInstrumentEntity);
        assertThat(authCardDetails.getAddress().isPresent(), is(false));
    }

    private void assertAuthCardDetails(AuthCardDetails authCardDetails, PaymentInstrumentEntity paymentInstrumentEntity) {
        assertThat(authCardDetails.getCardHolder(), is("Mr Test"));
        assertThat(authCardDetails.getEndDate().toString(), is("12/99"));

        assertThat(authCardDetails.getCardBrand(), is(paymentInstrumentEntity.getCardDetails().getCardBrand()));
        assertThat(authCardDetails.getPayersCardType(), is(PayersCardType.from(paymentInstrumentEntity.getCardDetails().getCardType())));
    }

}

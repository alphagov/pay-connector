package uk.gov.pay.connector.charge.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import uk.gov.pay.connector.paymentprocessor.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.paymentprocessor.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.paymentprocessor.model.LastDigitsCardNumber;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.northamericaregion.UsState;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthCardDetailsToCardDetailsEntityConverterTest {

    private static final String CARDHOLDER_NAME = "Ms Payment";
    private static final String CARD_NUMBER = "4000056655665556";
    private static final FirstDigitsCardNumber CARD_NUMBER_FIRST_6_DIGITS = FirstDigitsCardNumber.of("400005");
    private static final LastDigitsCardNumber CARD_NUMBER_LAST_4_DIGITS = LastDigitsCardNumber.of("5556");
    private static final CardExpiryDate CARD_EXPIRY_DATE = CardExpiryDate.valueOf("04/27");
    private static final String CARD_BRAND = "visa";
    private static final String ADDRESS_LINE_1 = "1 High Street";
    private static final String CITY = "Paytown";
    private static final String POSTCODE = "PAY ME";
    private static final String GB_COUNTRY_CODE = "GB";
    private static final String US_COUNTRY_CODE = "US";

    private AuthCardDetails authCardDetails;
    private Address address;

    @Mock
    private NorthAmericanRegionMapper mockNorthAmericanRegionMapper;

    private AuthCardDetailsToCardDetailsEntityConverter authCardDetailsToCardDetailsEntityConverter;

    @BeforeEach
    void setUp() {
        authCardDetails = new AuthCardDetails();
        authCardDetails.setCardHolder(CARDHOLDER_NAME);
        authCardDetails.setCardNo(CARD_NUMBER);
        authCardDetails.setEndDate(CARD_EXPIRY_DATE);
        authCardDetails.setCardBrand(CARD_BRAND);
        authCardDetails.setPayersCardType(PayersCardType.CREDIT);

        authCardDetailsToCardDetailsEntityConverter = new AuthCardDetailsToCardDetailsEntityConverter(mockNorthAmericanRegionMapper);
    }

    @Test
    void convertsAuthCardDetailsWithNoAddressToCardDetailsEntity() {
        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getCardHolderName(), is(CARDHOLDER_NAME));
        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(CARD_NUMBER_FIRST_6_DIGITS));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(CARD_NUMBER_LAST_4_DIGITS));
        assertThat(cardDetailsEntity.getExpiryDate(), is(CARD_EXPIRY_DATE));
        assertThat(cardDetailsEntity.getCardBrand(), is(CARD_BRAND));
        assertThat(cardDetailsEntity.getCardTypeDetails().isPresent(), is(false));
        assertThat(cardDetailsEntity.getCardType(), is(CardType.CREDIT));
        assertThat(cardDetailsEntity.getBillingAddress().isEmpty(), is(true));
    }

    @Test
    void convertsAuthCardDetailsWithFewerThan6DigitsInCardNumberToCardDetailsEntity() {
        authCardDetails.setCardNo("12345");

        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(nullValue()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(LastDigitsCardNumber.of("2345")));
    }

    @Test
    void convertsAuthCardDetailsWithFewerThan4DigitsInCardNumberToCardDetailsEntity() {
        authCardDetails.setCardNo("321");

        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(nullValue()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(nullValue()));
    }

    @Test
    void convertsAuthCardDetailsWithEmptyStringInCardNumberToCardDetailsEntity() {
        authCardDetails.setCardNo("");

        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getFirstDigitsCardNumber(), is(nullValue()));
        assertThat(cardDetailsEntity.getLastDigitsCardNumber(), is(nullValue()));
    }

    @Test
    void sanitisesSuspectedCardNumbers() {
        authCardDetails.setCardHolder("Mr 12345678910");
        authCardDetails.setCardBrand("A98765432100Z");

        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getCardHolderName(), is("Mr ***********"));
        assertThat(cardDetailsEntity.getCardBrand(), is("A***********Z"));
    }

    @Test
    void convertsAuthCardDetailsWithUkAddressToCardDetailsEntity() {
        address = new Address(ADDRESS_LINE_1, null, POSTCODE, CITY, null, GB_COUNTRY_CODE);
        authCardDetails.setAddress(address);

        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getBillingAddress().isPresent(), is(true));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine1(), is(ADDRESS_LINE_1));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine2(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCity(), is(CITY));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCounty(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getStateOrProvince(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCountry(), is(GB_COUNTRY_CODE));
    }

    @Test
    void convertsAuthCardDetailsWithUsAddressToCardDetailsEntityWithState() {
        address = new Address(ADDRESS_LINE_1, null, POSTCODE, CITY, null, US_COUNTRY_CODE);
        authCardDetails.setAddress(address);

        given(mockNorthAmericanRegionMapper.getNorthAmericanRegionForCountry(address))
                .willAnswer(((Answer<Optional<? extends NorthAmericaRegion>>) invocationOnMock -> Optional.of(UsState.WASHINGTON_DC)));

        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getBillingAddress().isPresent(), is(true));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine1(), is(ADDRESS_LINE_1));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine2(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCity(), is(CITY));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCounty(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getStateOrProvince(), is(UsState.WASHINGTON_DC.getAbbreviation()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCountry(), is(US_COUNTRY_CODE));
    }

    @Test
    void convertsAuthCardDetailsWithUsAddressWithUnrecognisedZipCodeToCardDetailsEntityWithNoState() {
        address = new Address(ADDRESS_LINE_1, null, POSTCODE, CITY, null, US_COUNTRY_CODE);
        authCardDetails.setAddress(address);

        given(mockNorthAmericanRegionMapper.getNorthAmericanRegionForCountry(address)).willReturn(Optional.empty());

        var cardDetailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

        assertThat(cardDetailsEntity.getBillingAddress().isPresent(), is(true));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine1(), is(ADDRESS_LINE_1));
        assertThat(cardDetailsEntity.getBillingAddress().get().getLine2(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCity(), is(CITY));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCounty(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getStateOrProvince(), is(nullValue()));
        assertThat(cardDetailsEntity.getBillingAddress().get().getCountry(), is(US_COUNTRY_CODE));
    }

}

package uk.gov.pay.connector.wallets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.paymentprocessor.model.LastDigitsCardNumber;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;

@ExtendWith(MockitoExtension.class)
class WalletPaymentInfoToAuthCardDetailsConverterTest {

    private static final String CARDHOLDER_NAME = "Wally Wallet";
    private static final String EMAIL = "wally.wallet@email.test";
    private static final LastDigitsCardNumber LAST_4_DIGITS = LastDigitsCardNumber.of("5678");
    private static final CardExpiryDate EXPIRY_DATE = CardExpiryDate.valueOf("04/27");
    private static final String CARD_BRAND = "master-card";

    private final WalletPaymentInfo walletPaymentInfo = anApplePayPaymentInfo()
            .withLastDigitsCardNumber(LAST_4_DIGITS.toString())
            .withBrand(CARD_BRAND)
            .withCardType(PayersCardType.CREDIT)
            .withCardholderName(CARDHOLDER_NAME)
            .withEmail(EMAIL)
            .build();

    private final WalletPaymentInfoToAuthCardDetailsConverter walletPaymentInfoToAuthCardDetailsConverter
            = new WalletPaymentInfoToAuthCardDetailsConverter();
    
    @Test
    void converts() {
        var authCardDetails = walletPaymentInfoToAuthCardDetailsConverter.convert(walletPaymentInfo, EXPIRY_DATE);

        assertThat(authCardDetails.getCardHolder(), is(CARDHOLDER_NAME));
        assertThat(authCardDetails.getCardNo(), is(LAST_4_DIGITS.toString()));
        assertThat(authCardDetails.getPayersCardType(), is(PayersCardType.CREDIT));
        assertThat(authCardDetails.getCardBrand(), is(CARD_BRAND));
        assertThat(authCardDetails.getEndDate(), is(EXPIRY_DATE));
        assertThat(authCardDetails.isCorporateCard(), is(false));
    }

    @Test
    void convertsWhenThereIsNotAnExpiryDate() {
        var authCardDetails = walletPaymentInfoToAuthCardDetailsConverter.convert(walletPaymentInfo, null);

        assertThat(authCardDetails.getCardHolder(), is(CARDHOLDER_NAME));
        assertThat(authCardDetails.getCardNo(), is(LAST_4_DIGITS.toString()));
        assertThat(authCardDetails.getPayersCardType(), is(PayersCardType.CREDIT));
        assertThat(authCardDetails.getCardBrand(), is(CARD_BRAND));
        assertThat(authCardDetails.getEndDate(), is(nullValue()));
        assertThat(authCardDetails.isCorporateCard(), is(false));
    }

    @Test
    void convertsWhenLast4DigitsOfCardNumberIsEmptyString() {
        WalletPaymentInfo walletPaymentInfo = anApplePayPaymentInfo()
                .withLastDigitsCardNumber("")
                .withBrand(CARD_BRAND)
                .withCardType(PayersCardType.CREDIT)
                .withCardholderName(CARDHOLDER_NAME)
                .withEmail(EMAIL)
                .build();

        var authCardDetails = walletPaymentInfoToAuthCardDetailsConverter.convert(walletPaymentInfo, EXPIRY_DATE);

        assertThat(authCardDetails.getCardHolder(), is(CARDHOLDER_NAME));
        assertThat(authCardDetails.getCardNo(), is(""));
        assertThat(authCardDetails.getPayersCardType(), is(PayersCardType.CREDIT));
        assertThat(authCardDetails.getCardBrand(), is(CARD_BRAND));
        assertThat(authCardDetails.getEndDate(), is(EXPIRY_DATE));
        assertThat(authCardDetails.isCorporateCard(), is(false));
    }

}

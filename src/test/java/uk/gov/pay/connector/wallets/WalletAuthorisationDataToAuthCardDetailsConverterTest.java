package uk.gov.pay.connector.wallets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WalletAuthorisationDataToAuthCardDetailsConverterTest {

    private static final String CARDHOLDER_NAME = "Wally Wallet";
    private static final String EMAIL = "wally.wallet@email.test";
    private static final LastDigitsCardNumber LAST_4_DIGITS = LastDigitsCardNumber.of("5678");
    private static final LocalDate EXPIRY_LOCAL_DATE = LocalDate.of(2027, 4, 30);
    private static final CardExpiryDate EXPIRY_DATE = CardExpiryDate.valueOf("04/27");
    private static final String CARD_BRAND = "master-card";

    @Mock
    private WalletAuthorisationData mockWalletAuthorisationData;

    private final WalletPaymentInfo walletPaymentInfo = new WalletPaymentInfo(LAST_4_DIGITS.toString(), CARD_BRAND,
            PayersCardType.CREDIT, CARDHOLDER_NAME, EMAIL);

    private final WalletAuthorisationDataToAuthCardDetailsConverter walletAuthorisationDataToAuthCardDetailsConverter
            = new WalletAuthorisationDataToAuthCardDetailsConverter();

    @BeforeEach
    void setUp() {
        given(mockWalletAuthorisationData.getPaymentInfo()).willReturn(walletPaymentInfo);
        given(mockWalletAuthorisationData.getCardExpiryDate()).willReturn(Optional.of(EXPIRY_LOCAL_DATE));
    }

    @Test
    void converts() {
        var authCardDetails = walletAuthorisationDataToAuthCardDetailsConverter.convert(mockWalletAuthorisationData);

        assertThat(authCardDetails.getCardHolder(), is(CARDHOLDER_NAME));
        assertThat(authCardDetails.getCardNo(), is(LAST_4_DIGITS.toString()));
        assertThat(authCardDetails.getPayersCardType(), is(PayersCardType.CREDIT));
        assertThat(authCardDetails.getCardBrand(), is(CARD_BRAND));
        assertThat(authCardDetails.getEndDate(), is(EXPIRY_DATE));
        assertThat(authCardDetails.isCorporateCard(), is(false));
    }

    @Test
    void convertsWhenThereIsNotAnExpiryDate() {
        given(mockWalletAuthorisationData.getCardExpiryDate()).willReturn(Optional.empty());

        var authCardDetails = walletAuthorisationDataToAuthCardDetailsConverter.convert(mockWalletAuthorisationData);

        assertThat(authCardDetails.getCardHolder(), is(CARDHOLDER_NAME));
        assertThat(authCardDetails.getCardNo(), is(LAST_4_DIGITS.toString()));
        assertThat(authCardDetails.getPayersCardType(), is(PayersCardType.CREDIT));
        assertThat(authCardDetails.getCardBrand(), is(CARD_BRAND));
        assertThat(authCardDetails.getEndDate(), is(nullValue()));
        assertThat(authCardDetails.isCorporateCard(), is(false));
    }

    @Test
    void convertsWhenLast4DigitsOfCardNumberIsEmptyString() {
        given(mockWalletAuthorisationData.getPaymentInfo())
                .willReturn(new WalletPaymentInfo("", CARD_BRAND, PayersCardType.CREDIT, CARDHOLDER_NAME, EMAIL));

        var authCardDetails = walletAuthorisationDataToAuthCardDetailsConverter.convert(mockWalletAuthorisationData);

        assertThat(authCardDetails.getCardHolder(), is(CARDHOLDER_NAME));
        assertThat(authCardDetails.getCardNo(), is(""));
        assertThat(authCardDetails.getPayersCardType(), is(PayersCardType.CREDIT));
        assertThat(authCardDetails.getCardBrand(), is(CARD_BRAND));
        assertThat(authCardDetails.getEndDate(), is(EXPIRY_DATE));
        assertThat(authCardDetails.isCorporateCard(), is(false));
    }

}

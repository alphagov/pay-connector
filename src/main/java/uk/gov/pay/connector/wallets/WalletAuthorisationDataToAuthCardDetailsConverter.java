package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.format.DateTimeFormatter;

public class WalletAuthorisationDataToAuthCardDetailsConverter {

    private static final DateTimeFormatter EXPIRY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/yy");

    public AuthCardDetails convert(WalletAuthorisationData walletAuthorisationData) {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardHolder(walletAuthorisationData.getPaymentInfo().getCardholderName());
        authCardDetails.setCardNo(walletAuthorisationData.getPaymentInfo().getLastDigitsCardNumber());
        authCardDetails.setPayersCardType(walletAuthorisationData.getPaymentInfo().getCardType());
        authCardDetails.setCardBrand(walletAuthorisationData.getPaymentInfo().getBrand());
        walletAuthorisationData.getCardExpiryDate().map(EXPIRY_DATE_FORMAT::format).map(CardExpiryDate::valueOf).ifPresent(authCardDetails::setEndDate);
        authCardDetails.setCorporateCard(false);
        return authCardDetails;
    }

}

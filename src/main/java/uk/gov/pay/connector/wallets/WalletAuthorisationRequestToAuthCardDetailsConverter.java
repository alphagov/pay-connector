package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.format.DateTimeFormatter;

public class WalletAuthorisationRequestToAuthCardDetailsConverter {

    public AuthCardDetails convert(WalletAuthorisationRequest walletAuthorisationRequest, CardExpiryDate cardExpiryDate) {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardHolder(walletAuthorisationRequest.getPaymentInfo().getCardholderName());
        authCardDetails.setCardNo(walletAuthorisationRequest.getPaymentInfo().getLastDigitsCardNumber());
        authCardDetails.setPayersCardType(walletAuthorisationRequest.getPaymentInfo().getCardType());
        authCardDetails.setCardBrand(walletAuthorisationRequest.getPaymentInfo().getBrand());
        authCardDetails.setEndDate(cardExpiryDate);
        authCardDetails.setCorporateCard(false);
        return authCardDetails;
    }

}

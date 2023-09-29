package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.service.payments.commons.model.CardExpiryDate;

public class WalletPaymentInfoToAuthCardDetailsConverter {

    public AuthCardDetails convert(WalletPaymentInfo walletPaymentInfo, CardExpiryDate cardExpiryDate) {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardHolder(walletPaymentInfo.getCardholderName());
        authCardDetails.setCardNo(walletPaymentInfo.getLastDigitsCardNumber());
        authCardDetails.setPayersCardType(walletPaymentInfo.getCardType());
        authCardDetails.setCardBrand(walletPaymentInfo.getBrand());
        authCardDetails.setEndDate(cardExpiryDate);
        authCardDetails.setCorporateCard(false);
        return authCardDetails;
    }

}

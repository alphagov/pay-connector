package uk.gov.pay.connector.card.util;

import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

public class PaymentInstrumentEntityToAuthCardDetailsConverter {

    public AuthCardDetails convert(PaymentInstrumentEntity paymentInstrumentEntity) {
        var cardDetails = paymentInstrumentEntity.getCardDetails();
        var authCardDetails = new AuthCardDetails();

        authCardDetails.setCardBrand(cardDetails.getCardBrand());
        authCardDetails.setCardHolder(cardDetails.getCardHolderName());
        authCardDetails.setEndDate(cardDetails.getExpiryDate());
        cardDetails.getBillingAddress()
                .map(Address::from)
                .ifPresent(authCardDetails::setAddress);
        authCardDetails.setPayersCardType(PayersCardType.from(cardDetails.getCardType()));
        return authCardDetails;
    }
}

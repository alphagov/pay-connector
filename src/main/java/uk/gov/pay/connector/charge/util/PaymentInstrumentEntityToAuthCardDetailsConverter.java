package uk.gov.pay.connector.charge.util;

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
        
        // @FIXME(sfount): if the card was identified as neither CREDIT NOR DEBIT this line will fail with an NPE
        if (cardDetails.getCardType() != null) {
            authCardDetails.setPayersCardType(PayersCardType.from(cardDetails.getCardType()));
        }
        return authCardDetails;
    }
}

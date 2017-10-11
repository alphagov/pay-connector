package uk.gov.pay.connector.model.domain;

import static uk.gov.pay.connector.model.domain.AddressEntityFixture.aValidAddressEntity;

public class CardholderDataEntityFixture {

    private String email = "cardholder@example.com";
    private String paymentRequestExternalId = "3ptc77nbck98onu0082egc91f7";

    public static CardholderDataEntityFixture aValidCardholderDataEntity() {
        return new CardholderDataEntityFixture();
    }

    public CardholderDataEntity build() {
        CardholderDataEntity entity = new CardholderDataEntity();
        entity.setBillingAddress(aValidAddressEntity().build());
        entity.setEmail(email);
        entity.setPaymentRequestExternalId(paymentRequestExternalId);
        return entity;
    }

    public CardholderDataEntityFixture withPaymentRequestExternalId(String paymentRequestExternalId) {
        this.paymentRequestExternalId = paymentRequestExternalId;
        return this;
    }

}

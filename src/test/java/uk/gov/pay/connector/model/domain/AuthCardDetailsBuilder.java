package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;

public final class AuthCardDetailsBuilder {
    private String cardNo = "4242424242424242";
    private String cardHolder = "Mr Test";
    private String cvc = "123";
    private String endDate = "12/99";
    private Address address = AddressFixture.aValidAddress().build();
    private String cardBrand = "VISA";
    private String userAgentHeader = "Mozilla/5.0";
    private String acceptHeader = "text/html";
    private PayersCardType payersCardType = PayersCardType.DEBIT;
    private Boolean corporateCard = Boolean.FALSE;

    private AuthCardDetailsBuilder() {
    }

    public static AuthCardDetailsBuilder anAuthCardDetails() {
        return new AuthCardDetailsBuilder();
    }

    public AuthCardDetailsBuilder withCardNo(String cardNo) {
        this.cardNo = cardNo;
        return this;
    }

    public AuthCardDetailsBuilder withCardHolder(String cardHolder) {
        this.cardHolder = cardHolder;
        return this;
    }

    public AuthCardDetailsBuilder withCvc(String cvc) {
        this.cvc = cvc;
        return this;
    }

    public AuthCardDetailsBuilder withEndDate(String endDate) {
        this.endDate = endDate;
        return this;
    }

    public AuthCardDetailsBuilder withAddress(Address address) {
        this.address = address;
        return this;
    }

    public AuthCardDetailsBuilder withCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }

    public AuthCardDetailsBuilder withUserAgentHeader(String userAgentHeader) {
        this.userAgentHeader = userAgentHeader;
        return this;
    }

    public AuthCardDetailsBuilder withAcceptHeader(String acceptHeader) {
        this.acceptHeader = acceptHeader;
        return this;
    }

    public AuthCardDetailsBuilder withCardType(PayersCardType payersCardType) {
        this.payersCardType = payersCardType;
        return this;
    }

    public AuthCardDetailsBuilder withCorporateCard(Boolean corporateCard) {
        this.corporateCard = corporateCard;
        return this;
    }

    public AuthCardDetails build() {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(cardNo);
        authCardDetails.setCardHolder(cardHolder);
        authCardDetails.setCvc(cvc);
        authCardDetails.setEndDate(endDate);
        authCardDetails.setAddress(address);
        authCardDetails.setCardBrand(cardBrand);
        authCardDetails.setUserAgentHeader(userAgentHeader);
        authCardDetails.setAcceptHeader(acceptHeader);
        authCardDetails.setPayersCardType(payersCardType);
        authCardDetails.setCorporateCard(corporateCard);
        return authCardDetails;
    }
}

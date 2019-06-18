package uk.gov.pay.connector.events.eventdetails;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

public class PaymentCreatedEventDetails extends EventDetails {
    private final Long amount;
    private final String description;
    private final String reference;
    private final String returnUrl;
    private final Long gatewayAccountId;
    private final  String paymentProvider;

    public PaymentCreatedEventDetails(Long amount, String description, String reference, String returnUrl, Long gatewayAccountId, String paymentProvider) {
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
        this.gatewayAccountId = gatewayAccountId;
        this.paymentProvider = paymentProvider;
    }

    public static PaymentCreatedEventDetails from(ChargeEntity charge) {
        return new PaymentCreatedEventDetails(
            charge.getAmount(),
            charge.getDescription(),
            charge.getReference().toString(),
            charge.getReturnUrl(),
            charge.getGatewayAccount().getId(),
            charge.getGatewayAccount().getGatewayName()           
        );
    }

    public Long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }
}

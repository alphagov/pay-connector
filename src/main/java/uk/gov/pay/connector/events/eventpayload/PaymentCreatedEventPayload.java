package uk.gov.pay.connector.events.eventpayload;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

public class PaymentCreatedEventPayload extends EventPayload {
    private final Long amount;
    private final String description;
    private final String reference;
    private final String returnUrl;
    private final Long gatewayAccountId;
    private final  String paymentProvider;

    private PaymentCreatedEventPayload(ChargeEntity charge) {
        this.amount = charge.getAmount();
        this.description = charge.getDescription();
        this.reference = charge.getReference().toString();
        this.returnUrl = charge.getReturnUrl();
        this.gatewayAccountId = charge.getGatewayAccount().getId();
        this.paymentProvider = charge.getGatewayAccount().getGatewayName();
    }

    public static PaymentCreatedEventPayload from(ChargeEntity charge) {
        return new PaymentCreatedEventPayload(charge);
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

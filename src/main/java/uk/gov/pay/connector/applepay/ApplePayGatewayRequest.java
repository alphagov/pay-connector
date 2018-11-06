package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Optional;

public class ApplePayGatewayRequest implements GatewayRequest {
    private ApplePaymentData authCardDetails;
    private ChargeEntity charge;

    public ApplePayGatewayRequest(ChargeEntity charge, ApplePaymentData authCardDetails) {
        this.charge = charge;
        this.authCardDetails = authCardDetails;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public ApplePaymentData getAuthCardDetails() {
        return authCardDetails;
    }

    public String getAmount() {
        return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
    }

    public String getDescription() {
        return charge.getDescription();
    }

    public String getChargeExternalId() {
        return String.valueOf(charge.getExternalId());
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    public static ApplePayGatewayRequest valueOf(ChargeEntity charge, ApplePaymentData authCardDetails) {
        return new ApplePayGatewayRequest(charge, authCardDetails);
    }
}

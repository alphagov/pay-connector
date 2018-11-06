package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Optional;

public class BaseAuthorisationGatewayRequest implements AuthorisationGatewayRequest {
    private AuthCardDetails authCardDetails;
    private ChargeEntity charge;

    public BaseAuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        this.charge = charge;
        this.authCardDetails = authCardDetails;
    }

    @Override
    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    @Override
    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    @Override
    public String getAmount() {
        return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
    }

    @Override
    public String getDescription() {
        return charge.getDescription();
    }

    @Override
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

    public static BaseAuthorisationGatewayRequest valueOf(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return new BaseAuthorisationGatewayRequest(charge, authCardDetails);
    }
}

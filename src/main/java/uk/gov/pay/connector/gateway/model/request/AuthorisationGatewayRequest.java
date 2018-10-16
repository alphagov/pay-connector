package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.util.charge.CorporateSurchargeCalculator;
import java.util.Optional;

public class AuthorisationGatewayRequest implements GatewayRequest {
    private AuthCardDetails authCardDetails;
    private ChargeEntity charge;

    public AuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        this.charge = charge;
        this.authCardDetails = authCardDetails;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    public String getAmount() {
        return String.valueOf(CorporateSurchargeCalculator.getTotalAmountFor(charge));
    }

    public String getDescription() {
        return charge.getDescription();
    }

    public String getChargeExternalId() {
        return String.valueOf(charge.getExternalId());
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    public static AuthorisationGatewayRequest valueOf(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return new AuthorisationGatewayRequest(charge, authCardDetails);
    }
}

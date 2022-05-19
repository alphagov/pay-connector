package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;
import java.util.Optional;

public abstract class AuthorisationGatewayRequest implements GatewayRequest {
    private final ChargeEntity charge;
    private final String gatewayTransactionId;
    
    protected AuthorisationGatewayRequest(ChargeEntity charge) {
        this.charge = charge;
        this.gatewayTransactionId = charge.getGatewayTransactionId();
    }
    
    protected AuthorisationGatewayRequest(ChargeEntity charge, String gatewayTransactionId) {
        this.charge = charge;
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public ChargeEntity getCharge() {
        return charge;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(gatewayTransactionId);
    }

    public String getAmount() {
        return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
    }

    public String getDescription() {
        return charge.getDescription();
    }

    public String getReference() {
        return charge.getReference().toString();
    }

    public String getChargeExternalId() {
        return charge.getExternalId();
    }

    @Override
    public Map<String, String> getGatewayCredentials() {
        return charge.getGatewayAccountCredentialsEntity().getCredentials();
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }
}

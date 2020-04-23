package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Optional;

public abstract class AuthorisationGatewayRequest implements GatewayRequest {
    protected ChargeEntity charge;
    
    public AuthorisationGatewayRequest(ChargeEntity charge) {
        this.charge = charge;
    }

    public ChargeEntity getCharge() {
        return charge;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public String getAmount() {
        return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
    }

    public String getDescription() {
        return charge.getDescription();
    }

    public String getChargeExternalId() {
        return charge.getExternalId();
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    public int getIntegrationVersion3ds() {
        return charge.getGatewayAccount().getIntegrationVersion3ds();
    }

    public boolean isRequires3ds() {
        return charge.getGatewayAccount().isRequires3ds();
    }
}

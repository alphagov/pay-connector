package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Map;

public class CancelGatewayRequest implements GatewayRequest {

    private ChargeEntity charge;

    private CancelGatewayRequest(ChargeEntity charge) {
        this.charge = charge;
    }

    public static CancelGatewayRequest valueOf(ChargeEntity charge) {
        return new CancelGatewayRequest(charge);
    }

    public String getTransactionId() {
        return charge.getGatewayTransactionId();
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.CANCEL;
    }

    @Override
    public Map<String, Object> getGatewayCredentials() {
        return charge.getGatewayAccountCredentialsEntity().getCredentials();
    }

    @Override
    public AuthorisationMode getAuthorisationMode() {
        return charge.getAuthorisationMode();
    }

    public String getExternalChargeId() {
        return charge.getExternalId();
    }

    public boolean isLiveAccount() {
        return charge.getGatewayAccount().isLive();
    }

    @Override
    public boolean isForRecurringPayment() {
        return charge.getAgreement().isPresent();
    }
}

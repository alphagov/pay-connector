package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.service.payments.commons.model.AuthorisationMode;

public record CancelGatewayRequest (
        ChargeEntity charge
) implements GatewayRequest {
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
    public GatewayCredentials getGatewayCredentials() {
        return charge.getGatewayAccountCredentialsEntity().getCredentialsObject();
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

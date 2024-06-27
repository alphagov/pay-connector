package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;

public record ChargeQueryGatewayRequest (
    
    GatewayAccountEntity gatewayAccount,
    GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity,
    String chargeExternalId,
    String transactionId,
    AuthorisationMode authorisationMode,
    boolean isForRecurringPayment
) implements GatewayRequest {
    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.QUERY;
    }

    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }

    @Override
    public GatewayCredentials getGatewayCredentials() {
        return gatewayAccountCredentialsEntity.getCredentialsObject();
    }

    @Override
    public boolean isForRecurringPayment() {
        return isForRecurringPayment;
    }

    public static ChargeQueryGatewayRequest valueOf(Charge charge, GatewayAccountEntity gatewayAccountEntity, GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
        return new ChargeQueryGatewayRequest(
                gatewayAccountEntity,
                gatewayAccountCredentialsEntity,
                charge.getExternalId(),
                charge.getGatewayTransactionId(),
                charge.getAuthorisationMode(),
                charge.getAgreementId().isPresent()
        );
    }
}

package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Map;

public class ChargeQueryGatewayRequest implements GatewayRequest {
    
    private final GatewayAccountEntity gatewayAccountEntity;
    private final GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;
    private final String chargeExternalId;
    private final String transactionId;
    private final AuthorisationMode authorisationMode;
    private boolean isForRecurringPayment;

    public ChargeQueryGatewayRequest(GatewayAccountEntity gatewayAccountEntity, GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity,
                                     String chargeExternalId, String transactionId, AuthorisationMode authorisationMode, boolean isForRecurringPayment) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        this.gatewayAccountCredentialsEntity = gatewayAccountCredentialsEntity;
        this.chargeExternalId = chargeExternalId;
        this.transactionId = transactionId;
        this.authorisationMode = authorisationMode;
        this.isForRecurringPayment = isForRecurringPayment;
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccountEntity;
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.QUERY;
    }

    public String getChargeExternalId() {
        return chargeExternalId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }

    @Override
    public Map<String, Object> getGatewayCredentials() {
        return gatewayAccountCredentialsEntity.getCredentials();
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

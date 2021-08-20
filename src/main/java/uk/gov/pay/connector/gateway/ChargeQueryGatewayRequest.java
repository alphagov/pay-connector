package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Map;

public class ChargeQueryGatewayRequest implements GatewayRequest {
    
    private final GatewayAccountEntity gatewayAccountEntity;
    private final GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;
    private final String chargeExternalId;
    private final String transactionId;

    public ChargeQueryGatewayRequest(GatewayAccountEntity gatewayAccountEntity, GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity, String chargeExternalId, String transactionId) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        this.gatewayAccountCredentialsEntity = gatewayAccountCredentialsEntity;
        this.chargeExternalId = chargeExternalId;
        this.transactionId = transactionId;
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

    @Override
    public Map<String, String> getGatewayCredentials() {
        return gatewayAccountCredentialsEntity.getCredentials();
    }
    
    public static ChargeQueryGatewayRequest valueOf(Charge charge, GatewayAccountEntity gatewayAccountEntity, GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
        return new ChargeQueryGatewayRequest(
                gatewayAccountEntity,
                gatewayAccountCredentialsEntity,
                charge.getExternalId(),
                charge.getGatewayTransactionId()
        );
    }
}

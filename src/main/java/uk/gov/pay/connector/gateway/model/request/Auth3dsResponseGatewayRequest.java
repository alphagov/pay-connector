package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import java.util.Optional;

public class Auth3dsResponseGatewayRequest implements GatewayRequest {

    private final ChargeEntity charge;
    private final Auth3dsDetails auth3DsDetails;


    public Auth3dsResponseGatewayRequest(ChargeEntity charge, Auth3dsDetails auth3DsDetails) {
        this.charge = charge;
        this.auth3DsDetails = auth3DsDetails;
    }

    public Auth3dsDetails getAuth3DsDetails() {
        return auth3DsDetails;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public String getChargeExternalId() {
        return String.valueOf(charge.getExternalId());
    }

    public Optional<String> getProviderSessionId() {
        return Optional.ofNullable(charge.getProviderSessionId());
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    public static Auth3dsResponseGatewayRequest valueOf(ChargeEntity charge, Auth3dsDetails auth3DsDetails) {
        return new Auth3dsResponseGatewayRequest(charge, auth3DsDetails);
    }
}

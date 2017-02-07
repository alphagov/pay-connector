package uk.gov.pay.connector.model.gateway;

import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.domain.Auth3dsResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Optional;

public class Auth3dsResponseGatewayRequest implements GatewayRequest {

    private final ChargeEntity charge;
    private final Auth3dsResponse auth3dsResponse;


    public Auth3dsResponseGatewayRequest(ChargeEntity charge, Auth3dsResponse auth3dsResponse) {
        this.charge = charge;
        this.auth3dsResponse = auth3dsResponse;
    }

    public Auth3dsResponse getAuth3dsResponse() {
        return auth3dsResponse;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public String getChargeExternalId() {
        return String.valueOf(charge.getExternalId());
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    public static Auth3dsResponseGatewayRequest valueOf(ChargeEntity charge, Auth3dsResponse auth3dsResponse) {
        return new Auth3dsResponseGatewayRequest(charge, auth3dsResponse);
    }
}

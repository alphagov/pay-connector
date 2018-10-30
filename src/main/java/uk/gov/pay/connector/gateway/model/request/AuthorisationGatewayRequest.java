package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.util.Optional;

public interface AuthorisationGatewayRequest extends GatewayRequest {
    
    Optional<String> getTransactionId();

    AuthCardDetails getAuthCardDetails();

    String getAmount();

    String getDescription();

    String getChargeExternalId();
}
